@file:Suppress("NOTHING_TO_INLINE")

package wumo.problem

import wumo.model.*
import wumo.model.Possible
import wumo.util.Rand
import wumo.util.Vector2
import wumo.util.Vector2.Companion.ZERO
import wumo.util.max
import wumo.util.rand
import java.util.*

object FlyPlane {
  data class RigidBody(val loc: Vector2, val radius: Double)
  
  val fieldWidth = 600.0
  private val numActions = 3
  private val numSimulation = 1
  private val Δt = 1.0
  private val max_acc = 1.0
  val target = RigidBody(Vector2(550.0, 50.0), 50.0)
  val plane = RigidBody(Vector2(0.0, 600.0), 20.0)
  private val initVel = Vector2(7.0, -7.0)
  private val maxFuel = 10000.0
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
      val stage: Int,
      val remainStages: LinkedList<Int>,
      val winsPerStage: IntArray
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
              nextStage = if (remainStages.isEmpty()) -1 else remainStages.pop()
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
              nextFuel = fuel - 1.0
              val obstacles = stageObstacles[stage]
              reward = loc.dist(target.loc) - nextLoc.dist(target.loc)
              when {
                nextLoc.outOf(0.0, 0.0, fieldWidth, fieldWidth) ||//out of field
                nextFuel < 0 -> {//out of fuel
                  reward = -1000.0
                  nextStage = -1
                }
                collide(nextLoc, obstacles) -> {//hit obstacle
                  reward = -2000.0
                  nextStage = -1
                }
                collide(nextLoc, target) -> {//hit destination
                  reward = 2000.0 - (maxFuel - nextFuel)
                  nextLoc.set(target.loc)
                  nextFuel = maxFuel
                  nextVel.set(initVel)
                  nextStage = stage
                }
              }
            }
            Possible(PlaneState(nextLoc, nextVel, nextFuel, nextStage, remainStages, winsPerStage), reward)
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
  
  fun makeRand(minObstacleRadius: Double = 50.0,
               maxObstacleRadius: Double = 100.0,
               γ: Double = 1.0): MDP {
    FlyPlane.numObstaclesPerStage = minOf(numObstaclesPerStage, 16)
    FlyPlane.γ = γ
    stageObstacles = Array(maxStage) {
      val iter = (0 until 16).shuffled().iterator()
      Array(numObstaclesPerStage) {
        val idx = iter.next()
        val x = idx / 4
        val y = idx % 4
        RigidBody(Vector2(150.0 + 100.0 * x, 150.0 + 100.0 * y), Rand().nextDouble(minObstacleRadius, maxObstacleRadius))
      }
    }
    winsPerStage = IntArray(maxStage) { 0 }
    visitsPerStage = IntArray(maxStage) { 0 }
    return DefaultMDP(γ) {
      val max = max(0 until maxStage) { winsPerStage[it].toDouble() } + 1
//      val start = rand(0 until maxStage) { exp(-winsPerStage[it].toDouble()) }
      val start = rand(0 until maxStage) { max - winsPerStage[it] }
      val remainStages = LinkedList<Int>().apply {
        for (s in start until maxStage)
          add(s)
      }
      visitsPerStage[start]++
      PlaneState(loc = plane.loc,
                 vel = initVel,
                 fuel = maxFuel,
                 stage = remainStages.removeFirst(),
                 remainStages = remainStages,
                 winsPerStage = winsPerStage)
    }
  }
}