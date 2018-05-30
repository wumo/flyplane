@file:Suppress("NOTHING_TO_INLINE")

package wumo.problem

import wumo.model.*
import wumo.model.Possible
import wumo.util.Rand
import wumo.util.Vector2
import wumo.util.Vector2.Companion.ZERO
import wumo.util.max
import wumo.util.rand
import kotlin.math.exp

object FlyPlane {
  data class RigidBody(val loc: Vector2, val radius: Double)
  
  var fieldWidth = 1350.0
  val scale = fieldWidth / 600
  private val numActions = 3
  private val numSimulation = 1
  private val Δt = 1.0
  private val max_acc = 1.0 * scale
  val target = RigidBody(Vector2(fieldWidth - 50 * scale, 50.0 * scale), 50.0 * scale)
  val plane = RigidBody(Vector2(0.0, fieldWidth), 20.0 * scale)
  private val initVel = Vector2(7.0 * scale, -7.0 * scale)
  private val maxFuel = 10000.0 * scale
  var maxStage: Int = 5
  var numObstaclesPerStage = 1
  lateinit var stageObstacles: Array<Array<RigidBody>>
  lateinit var winsPerStage: IntArray
  lateinit var visitsPerStage: IntArray
  private var γ: Double = 1.0
  
  class PlaneState(
      val loc: Vector2,
      val vel: Vector2,
      val fuel: Double,
      val stage: Int
  ) : State {
    override val actions: Array<Action<State>>? =
        if (stage == -1) null
        else Array(numActions) {
          val a = it
          DefaultAction(a) {
            val nextLoc = loc.copy()
            val nextVel = vel.copy()
            var nextFuel: Double
            var nextStage = stage
            var reward: Double
            if (collide(loc, target)) {
              winsPerStage[stage]++
              nextLoc.set(plane.loc)
              nextVel.set(initVel)
              nextFuel = maxFuel
              nextStage = if (stage + 1 < maxStage) stage + 1 else -1
              if (nextStage != -1) visitsPerStage[nextStage]++
              reward = 0.0
            } else {
              repeat(numSimulation) {
                val dir = nextVel.copy().norm()
                val acc = when (a) {
                  1 -> //left
                    dir.rot90L() * max_acc
                  2 -> //right
                    dir.rot90R() * max_acc
                  else ->//no op
                    ZERO
                }
                nextLoc += nextVel * Δt + acc * (Δt * Δt * 0.5)
                nextVel += acc * Δt
              }
              nextFuel = fuel - 1.0 * scale
              val obstacles = stageObstacles[stage]
              reward = (loc.dist(target.loc) - nextLoc.dist(target.loc)) * scale
              when {
                nextLoc.outOf(0.0, 0.0, fieldWidth, fieldWidth) ||//out of field
                nextFuel < 0 -> {//out of fuel
                  reward = -1000.0 * scale
                  nextStage = -1
                }
                collide(nextLoc, obstacles) -> {//hit obstacle
                  reward = -2000.0 * scale
                  nextStage = -1
                }
                collide(nextLoc, target) -> {//hit destination
                  reward = 2000.0 * scale - (maxFuel - nextFuel)
                  nextLoc.set(target.loc)
                  nextFuel = maxFuel
                  nextVel.set(initVel)
                  nextStage = stage
                }
              }
            }
            Possible(PlaneState(nextLoc, nextVel, nextFuel, nextStage), reward)
          }
        }
    
  }
  
  inline fun collide(loc: Vector2, obstacle: RigidBody): Boolean {
    if (loc.dist(obstacle.loc) <= obstacle.radius)
      return true
    return false
  }
  
  fun collide(loc: Vector2, obstacles: Array<RigidBody>): Boolean {
    val size = obstacles.size
    for (i in 0 until size)
      if (collide(loc, obstacles[i]))
        return true
    return false
  }
  
  fun makeRand(maxObstacleRadius: Double = 100.0,
               γ: Double = 1.0): MDP {
    val maxObstacleRadius = maxObstacleRadius * scale
    FlyPlane.numObstaclesPerStage = numObstaclesPerStage + 1
    FlyPlane.γ = γ
    val randRadius = {
      if (Rand().nextBoolean()) 0.0 else Rand().nextDouble(0.0, maxObstacleRadius)
    }
    stageObstacles = Array(maxStage) {
      Array(numObstaclesPerStage) {
        when (it) {
          0 -> RigidBody(Vector2(50.0 * scale, 50.0 * scale), randRadius())
          1 -> RigidBody(Vector2(fieldWidth - 50.0 * scale, fieldWidth - 50.0 * scale), randRadius())
          else -> {
            val loc = Vector2(Rand().nextDouble(100 * scale, fieldWidth - 100 * scale),
                              Rand().nextDouble(0 * scale, fieldWidth - 0 * scale))
            var radius = randRadius()
            radius = maxOf(0.0, minOf(radius, loc.dist(target.loc) - target.radius))
            RigidBody(loc, radius)
          }
        }
      }
    }
    winsPerStage = IntArray(maxStage) { 0 }
    visitsPerStage = IntArray(maxStage) { 0 }
    return DefaultMDP(γ) {
      //      var sum = 0.0
//      for (i in 0 until maxStage)
//        sum += exp(-visitsPerStage[i].toDouble())
      val max = max(0 until maxStage) { visitsPerStage[it].toDouble() } + 1
      val start = rand(0 until maxStage) { max - visitsPerStage[it] }
//      val start = rand(0 until maxStage) { exp(-visitsPerStage[it].toDouble()) / sum }
//      val start = Rand().nextInt(maxStage)
      visitsPerStage[start]++
      PlaneState(loc = plane.loc,
                 vel = initVel,
                 fuel = maxFuel,
                 stage = start)
    }
  }
}