@file:Suppress("NOTHING_TO_INLINE")

package wumo.problem

import wumo.experiment.maxObstacleRadius
import wumo.model.*
import wumo.model.Possible
import wumo.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object FlyPlane {
  data class RigidBody(val loc: Vector2, val radius: Double)
  
  var fieldWidth = 1350.0
  val scale = fieldWidth / 600
  private val numActions = 3
  private val Δt = 1.0
  private val max_acc = 1.0 * scale
  val target = RigidBody(Vector2(fieldWidth - 50 * scale, 50.0 * scale), 50.0 * scale)
  val plane = RigidBody(Vector2(0.0, fieldWidth), 20.0 * scale)
  private val initVel = Vector2(10.0 * scale, (-45.0).toRadian())
  private val maxFuel = 10000.0 * scale
  var maxScene: Int = 5
  var numObstaclesPerScene = 1
  lateinit var sceneObstacles: Array<Array<RigidBody>>
  lateinit var winsPerScene: IntArray
  lateinit var visitsPerScene: IntArray
  private var γ: Double = 1.0
  val terminalState = PlaneState(Vector2(), Vector2(), 0.0, -1, true)
  var determinedStartScene = -1
  
  class PlaneState(
      val loc: Vector2,
      val vel: Vector2,
      val fuel: Double,
      val scene: Int,
      val nextTerminal: Boolean
  ) : State {
    override val actions: Array<Action<State>>? =
        if (scene == -1) null
        else Array(numActions) {
          val a = it
          DefaultAction(a, fun(): Possible<State> {
            if (nextTerminal)
              return Possible(terminalState, 0.0)
            if (collide(loc, target)) {
              winsPerScene[scene]++
              return Possible(terminalState, 0.0)
            }
            val nextLoc = loc.copy()
            val nextVel = vel.copy()
            val (v, θ) = nextVel
            var nextFuel: Double
            var nextScene = scene
            var reward: Double
            if (a == 0)
              nextLoc.plus(Δt * v * cos(θ), Δt * v * sin(θ))
            else {
              val w = max_acc / v
              val Δθ = w * Δt
              val R = v * v / max_acc
              
              when (a) {
                1 -> { //left
                  val angle = θ + PI / 2 + PI + Δθ
                  val angle2 = θ + PI / 2
                  val dir = Vector2(R * (cos(angle) + cos(angle2)), R * (sin(angle) + sin(angle2)))
                  nextLoc += dir
                  nextVel.y = (θ + Δθ) % (2 * PI)
                }
                2 -> { //right
                  val angle = θ + PI / 2 - Δθ
                  val angle2 = θ - PI / 2
                  val dir = Vector2(R * (cos(angle) + cos(angle2)), R * (sin(angle) + sin(angle2)))
                  nextLoc += dir
                  nextVel.y = (θ - Δθ) % (2 * PI)
                }
              }
              if (nextVel.y < 0)
                nextVel.y = nextVel.y + 2 * PI
//                nextLoc += nextVel * Δt + center * (Δt * Δt * 0.5)
//                nextVel += center * Δt
            }
            nextFuel = fuel - 1.0 * scale
            val obstacles = sceneObstacles[scene]
//              reward = (loc.dist(target.loc) - nextLoc.dist(target.loc)) * scale
//              reward = (loc.dist(target.loc) - nextLoc.dist(target.loc) - 0.1) * scale
//              reward = (loc.dist(target.loc) - nextLoc.dist(target.loc)) * scale
            reward = 0.0
            when {
              nextLoc.outOf(0.0, 0.0, fieldWidth, fieldWidth) ||//out of field
              nextFuel < 0 -> {//out of fuel
                reward = -100.0 * scale
                nextScene = -1
              }
              collide(nextLoc, obstacles) -> {//hit obstacle
                reward = -100.0 * scale
                nextScene = -1
              }
              collide(nextLoc, target) -> {//hit destination
                reward = 2000.0 * scale - (maxFuel - nextFuel)
//                  reward = 2000.0 * scale
                nextLoc.set(target.loc)
                nextFuel = maxFuel
                nextVel.set(initVel)
                nextScene = scene
              }
            }
            return Possible(if (nextScene == -1) terminalState
                            else PlaneState(nextLoc, nextVel, nextFuel, nextScene, false), reward)
          })
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
  
  @Suppress("NAME_SHADOWING")
  fun makeRand(maxObstacleRadius: Double = 100.0,
               γ: Double = 1.0): MDP {
    val maxObstacleRadius = maxObstacleRadius * scale
    FlyPlane.numObstaclesPerScene = numObstaclesPerScene + 1
    FlyPlane.γ = γ
    val randRadius = {
      //      if (Rand().nextBoolean()) 0.0 else Rand().nextDouble(0.0, maxObstacleRadius)
      Rand().nextDouble(0.0, maxObstacleRadius)
    }
    sceneObstacles = Array(maxScene) {
      Array(numObstaclesPerScene) {
        var radius = randRadius()
        val loc = Vector2(Rand().nextDouble(radius + plane.radius, fieldWidth - radius - plane.radius),
                          Rand().nextDouble(radius + plane.radius, fieldWidth - radius - plane.radius))
        radius = maxOf(0.0, minOf(radius, loc.dist(target.loc) - target.radius))
        radius = maxOf(0.0, minOf(radius, loc.dist(plane.loc) - plane.radius * 2))
        RigidBody(loc, radius)
      }
    }
    winsPerScene = IntArray(maxScene) { 0 }
    visitsPerScene = IntArray(maxScene) { 0 }
    return DefaultMDP(γ) {
      //      var sum = 0.0
//      for (i in 0 until maxScene)
//        sum += exp(-visitsPerScene[i].toDouble())
      val start = if (determinedStartScene == -1) {
        val max = max(0 until maxScene) { visitsPerScene[it].toDouble() } + 1
        rand(0 until maxScene) { max - visitsPerScene[it] }
      } else
        determinedStartScene
//      val start = rand(0 until maxScene) { exp(-visitsPerScene[it].toDouble()) / sum }
//      val start = Rand().nextInt(maxScene)
      visitsPerScene[start]++
      PlaneState(loc = plane.loc,
                 vel = initVel,
                 fuel = maxFuel,
                 scene = start,
                 nextTerminal = false)
    }
  }
  
  fun makeSpecific(): MDP {
    numObstaclesPerScene = 8
    sceneObstacles = Array(maxScene) {
      arrayOf(RigidBody(Vector2(100.0 * scale, 400.0 * scale), 100.0 * scale),
              RigidBody(Vector2(250.0 * scale, 400.0 * scale), 50.0 * scale),
              RigidBody(Vector2(500.0 * scale, 500.0 * scale), 100.0 * scale),
              RigidBody(Vector2(350.0 * scale, 200.0 * scale), 50.0 * scale),
              RigidBody(Vector2(430.0 * scale, 200.0 * scale), 50.0 * scale),
              RigidBody(Vector2(500.0 * scale, 200.0 * scale), 50.0 * scale),
              RigidBody(Vector2(560.0 * scale, 200.0 * scale), 50.0 * scale),
              RigidBody(Vector2(400.0 * scale, 25.0 * scale), 25.0 * scale))
    }
    winsPerScene = IntArray(maxScene) { 0 }
    visitsPerScene = IntArray(maxScene) { 0 }
    return DefaultMDP(γ) {
      //      var sum = 0.0
//      for (i in 0 until maxScene)
//        sum += exp(-visitsPerScene[i].toDouble())
      val start = if (determinedStartScene == -1) {
        val max = max(0 until maxScene) { visitsPerScene[it].toDouble() } + 1
        rand(0 until maxScene) { max - visitsPerScene[it] }
      } else
        determinedStartScene
//      val start = rand(0 until maxScene) { exp(-visitsPerScene[it].toDouble()) / sum }
//      val start = Rand().nextInt(maxScene)
      visitsPerScene[start]++
      PlaneState(loc = plane.loc,
                 vel = initVel,
                 fuel = maxFuel,
                 scene = start,
                 nextTerminal = false)
    }
  }
}