@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package wumo.experiment

import javafx.application.Application
import javafx.application.Platform
import javafx.scene.chart.XYChart
import javafx.scene.paint.Color
import javafx.scene.text.Font
import wumo.algorithm.*
import wumo.model.DefaultAction
import wumo.model.isTerminal
import wumo.problem.FlyPlane
import wumo.util.AreaChartDescription
import wumo.util.D2DGameUI
import wumo.util.D2DGameUI.Companion.afterStartup
import wumo.util.D2DGameUI.Companion.canvas_height
import wumo.util.D2DGameUI.Companion.canvas_width
import wumo.util.D2DGameUI.Companion.charts
import wumo.util.D2DGameUI.Companion.height
import wumo.util.D2DGameUI.Companion.width
import wumo.util.LineChartDescription
import wumo.util.Vector2
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.math.*

var numTilesPerTiling = 1000
var _numTilings = 16
var episode_round = 100
var step_round = 2000
var max_episode = 2000_0000
var ε = 0.1
var _α = 0.6
var α_episode = 1000
var maxObstacleRadius = 100.0

inline fun setting_100_30() {
  FlyPlane.maxStage = 100
  FlyPlane.numObstaclesPerStage = 30
  maxObstacleRadius = 50.0
  
  numTilesPerTiling = 8_0000
  _numTilings = 16
  episode_round = 100
  step_round = 100
  max_episode = 2000_0000
}

inline fun setting_10_30() {
  FlyPlane.maxStage = 10
  FlyPlane.numObstaclesPerStage = 30
  numTilesPerTiling = 2_0000
  _numTilings = 16
  episode_round = 100
  step_round = 100
}

inline fun setting_10_10() {
  FlyPlane.maxStage = 10
  FlyPlane.numObstaclesPerStage = 10
  numTilesPerTiling = 2_0000
  _numTilings = 16
  episode_round = 100
  step_round = 100
}

inline fun setting_1_6() {
  FlyPlane.maxStage = 10
  FlyPlane.numObstaclesPerStage = 6
  numTilesPerTiling = 1_0000
  _numTilings = 16
  episode_round = 100
  step_round = 100
}

inline fun setting_2_30() {
  FlyPlane.maxStage = 2
  FlyPlane.numObstaclesPerStage = 30
  maxObstacleRadius = 50.0
  
  numTilesPerTiling = 4_0000
  _numTilings = 16
  episode_round = 100
  step_round = 100
  max_episode = 2_0000
}

inline fun custom_setting() {
  FlyPlane.maxStage = 10
  FlyPlane.numObstaclesPerStage = 30
  numTilesPerTiling = 4_0000
  _numTilings = 16
  episode_round = 100
  step_round = 100
}

fun main(args: Array<String>) {
  setting_100_30()
//  setting_10_10()
//  setting_1_6()
//  setting_2_30()
//  custom_setting()
  val resolution = 100
  val unit = FlyPlane.fieldWidth / resolution
  val m = ceil(sqrt(FlyPlane.maxStage.toDouble())).toInt()
  val stageWidth = FlyPlane.fieldWidth / m
  val stageScale = 1.0 / m
  fun Double.transX(stage: Int) = stageWidth * (stage % m) + this * stageScale
  fun Double.transY(stage: Int) = stageWidth * (stage / m) + this * stageScale
  fun Double.tranUnit() = this * stageScale
  val feature = SuttonTileCoding(numTilesPerTiling = numTilesPerTiling, _numTilings = _numTilings, allowCollisions = true) { s, a, tilesFunc ->
    s as FlyPlane.PlaneState
    a as DefaultAction<Int, FlyPlane.PlaneState>
    val floats = ArrayList<Double>(FlyPlane.numObstaclesPerStage + 4).apply {
      add(s.loc.x / resolution);add(s.loc.y / resolution);add(s.vel.x);add(s.vel.y / 10)
      for (i in 0 until FlyPlane.numObstaclesPerStage) {
        val obstacle = FlyPlane.stageObstacles[s.stage][i]
        add(obstacle.loc.x / resolution);add(obstacle.loc.y / resolution);add(obstacle.radius / resolution)
      }
    }.toDoubleArray()
    tilesFunc(floats, intArrayOf(a.value))
  }
  
  val func = LinearTileCodingFunc(feature)
  val numTilings = feature.numTilings
  
  val qvalue = Array(FlyPlane.maxStage) {
    Array(resolution + 1) {
      Array(resolution + 1) {
        Double.NEGATIVE_INFINITY
      }
    }
  }
  var animate = false
  val latch = CountDownLatch(1)
  thread(isDaemon = true) {
    latch.await()
    val ix = Math.floor(FlyPlane.plane.loc.x / unit).toInt()
    val iy = Math.floor(FlyPlane.plane.loc.y / unit).toInt()
    var oMax: Double
    var oMin: Double
    var mMax = Double.NEGATIVE_INFINITY
    var mMin = Double.POSITIVE_INFINITY
    val start = System.currentTimeMillis()
    var last = System.currentTimeMillis()
    var stepSum = 0
    val preWinsPerStage = IntArray(FlyPlane.maxStage)
    val winRate = DoubleArray(FlyPlane.maxStage)
    val preVisitsPerStage = IntArray(FlyPlane.maxStage)
    val changedEpisode = IntArray(FlyPlane.maxStage)
    val αStage = DoubleArray(FlyPlane.maxStage) { _α }
    val episodeListener: EpisodeListener = { episode, step, st ->
      stepSum += step
      st as FlyPlane.PlaneState
      val current = System.currentTimeMillis()
      if (current - last > 5000) {
        val time = (current - start) / 1000.0
        val deltaTime = (current - last) / 1000.0
        val speed = stepSum / deltaTime
        Platform.runLater {
          (D2DGameUI.charts[5] as LineChartDescription).data.apply {
            this[0].add(XYChart.Data(time.toInt(), speed.toInt()))
          }
        }
        stepSum = 0
        last = current
      }
//      D2DGameUI.render {
//        D2DGameUI.title = "episode=$episode,total_step=$step\t,max=${oMax.format(2)},min=${oMin.format(2)}"
//      }
      if (episode % episode_round == 0) {
        Platform.runLater {
          (D2DGameUI.charts[0] as AreaChartDescription).data.apply {
            if (this[0].isEmpty())
              for (i in 0 until FlyPlane.maxStage) {
                val q = qvalue[i][ix][iy]
                this[0].add(XYChart.Data(i, q))
              }
            else
              for (i in 0 until FlyPlane.maxStage) {
                val q = qvalue[i][ix][iy]
                this[0][i].yValue = q
              }
          }
          (D2DGameUI.charts[1] as AreaChartDescription).data.apply {
            if (this[0].isEmpty())
              for (i in 0 until FlyPlane.maxStage) {
                val visits = maxOf(FlyPlane.visitsPerStage[i] - preVisitsPerStage[i], 1)
                winRate[i] = (FlyPlane.winsPerStage[i] - preWinsPerStage[i]) / visits.toDouble()
                this[0].add(XYChart.Data(i, winRate[i]))
                preWinsPerStage[i] = FlyPlane.winsPerStage[i]
              }
            else
              for (i in 0 until FlyPlane.maxStage) {
                val visits = maxOf(FlyPlane.visitsPerStage[i] - preVisitsPerStage[i], 1)
                winRate[i] = (FlyPlane.winsPerStage[i] - preWinsPerStage[i]) / visits.toDouble()
                this[0][i].yValue = winRate[i]
                preWinsPerStage[i] = FlyPlane.winsPerStage[i]
              }
          }
          (D2DGameUI.charts[2] as AreaChartDescription).data.apply {
            System.arraycopy(FlyPlane.visitsPerStage, 0, preVisitsPerStage, 0, FlyPlane.maxStage)
            if (this[0].isEmpty())
              for (i in 0 until FlyPlane.maxStage)
                this[0].add(XYChart.Data(i, FlyPlane.visitsPerStage[i]))
            else
              for (i in 0 until FlyPlane.maxStage)
                this[0][i].yValue = FlyPlane.visitsPerStage[i]
          }
          (D2DGameUI.charts[3] as AreaChartDescription).data.apply {
            var sum = 0
            for (i in 0 until FlyPlane.maxStage)
              sum += FlyPlane.visitsPerStage[i]
            val max = wumo.util.max(0 until FlyPlane.maxStage) { FlyPlane.visitsPerStage[it].toDouble() } + 1
            val base = max * FlyPlane.maxStage - sum
            if (this[0].isEmpty())
              for (i in 0 until FlyPlane.maxStage)
                this[0].add(XYChart.Data(i, (max - FlyPlane.visitsPerStage[i]) / base))
            else
              for (i in 0 until FlyPlane.maxStage)
                this[0][i].yValue = (max - FlyPlane.visitsPerStage[i]) / base
          }
          (D2DGameUI.charts[4] as LineChartDescription).data.apply {
            this[0].add(XYChart.Data(episode, feature.numOfComponents))
            this[1].add(XYChart.Data(episode, feature.data.size))
          }
          (D2DGameUI.charts[6] as AreaChartDescription).data.apply {
            if (this[0].isEmpty())
              for (i in 0 until FlyPlane.maxStage)
                this[0].add(XYChart.Data(i, αStage[i]))
            else
              for (i in 0 until FlyPlane.maxStage)
                this[0][i].yValue = αStage[i]
          }
        }
        if (animate)
          Thread.sleep(1000L)
      }
    }
    val stepListener: StepListener = step@{ episode, step, s, a ->
      s as FlyPlane.PlaneState
      a as DefaultAction<Int, FlyPlane.PlaneState>
      
      val cx = floor(s.loc.x / unit).toInt()
      val cy = floor(s.loc.y / unit).toInt()
      if (s.stage != -1) {
        qvalue[s.stage][cx][cy] = func(s, a)
        mMax = maxOf(mMax, qvalue[s.stage][cx][cy])
        mMin = minOf(mMin, qvalue[s.stage][cx][cy])
      }
//        qvalue[s.stage][cx][cy] = maxOf(qvalue[s.stage][cx][cy], func(s, a))
      if (episode % step_round != 0) return@step
      if (!animate && step > 1) return@step
      D2DGameUI.render { gc ->
        gc.clearRect(0.0, 0.0, FlyPlane.fieldWidth, FlyPlane.fieldWidth)
        gc.lineWidth = 3.0
        oMax = Double.NEGATIVE_INFINITY
        oMin = Double.POSITIVE_INFINITY
        for (stage in 0 until FlyPlane.maxStage) {
          var max = Double.NEGATIVE_INFINITY
          var min = Double.POSITIVE_INFINITY
          for (nx in 0 until resolution)
            for (ny in 0 until resolution) {
              val q = qvalue[stage][nx][ny]
              if (q.isInfinite()) continue
              max = maxOf(max, q)
              min = minOf(min, q)
            }
          oMax = maxOf(oMax, max)
          oMin = minOf(oMin, min)
          for (nx in 0 until resolution)
            for (ny in 0 until resolution) {
              var q = qvalue[stage][nx][ny]
              if (q.isInfinite())
                q = min
              //draw Q value
              gc.fill = Color.BLUE.interpolate(Color.RED, if (max == min) 0.5 else (q - min) / (max - min))
              gc.fillRect((nx * unit).transX(stage) - 1, (ny * unit).transY(stage) - 1, unit.tranUnit() + 2, unit.tranUnit() + 2)
            }
          //draw target per stage
          gc.fill = Color.GREEN
          gc.fillOval((FlyPlane.target.loc.x - FlyPlane.target.radius).transX(stage),
                      (FlyPlane.target.loc.y - FlyPlane.target.radius).transY(stage),
                      2 * FlyPlane.target.radius.tranUnit(),
                      2 * FlyPlane.target.radius.tranUnit())
          //draw obstacles per stage
          gc.fill = Color.GREY
          for (i in 0 until FlyPlane.numObstaclesPerStage) {
            val obstacle = FlyPlane.stageObstacles[stage][i]
            val oLoc = obstacle.loc
            val oRadius = obstacle.radius
            gc.fillOval((oLoc.x - oRadius).transX(stage), (oLoc.y - oRadius).transY(stage),
                        2 * oRadius.tranUnit(), 2 * oRadius.tranUnit())
          }
          //draw edge per stage
          gc.stroke = Color.BLACK
          gc.strokeRect(0.0.transX(stage), 0.0.transY(stage), FlyPlane.fieldWidth.tranUnit(), FlyPlane.fieldWidth.tranUnit())
          gc.fill = Color.YELLOW
          gc.fillText("$stage", 10.0.transX(stage), 90.0.transY(stage))
        }
        with(s) {
          gc.fill = Color.ORANGE
          val vel = Vector2(s.vel.x * cos(s.vel.y), s.vel.x * sin(s.vel.y))
          val dir = vel.norm() * FlyPlane.plane.radius
          val top = s.loc + dir
          val left = s.loc + (dir / 2.0).rot90L()
          val right = s.loc + (dir / 2.0).rot90R()
          gc.fillPolygon(doubleArrayOf(top.x.transX(stage), left.x.transX(stage), right.x.transX(stage)),
                         doubleArrayOf(top.y.transY(stage), left.y.transY(stage), right.y.transY(stage)),
                         3)
        }
      }
      if (animate)
        Thread.sleep((1.0 / 10 * 1000).toLong())
    }
    val prob = FlyPlane.makeRand(maxObstacleRadius)
    animate = false
    prob.`Specialized True Online Sarsa(λ)`(
        Qfunc = func,
        π = EpsilonGreedyFunctionPolicy(func, ε),
        λ = 0.96,
        α = { _, _ ->
          _α / numTilings
        },
//        α = { startState, episode ->
//          startState as FlyPlane.PlaneState
//          val i = startState.stage
//          if (winRate[i] > 0.0) {
//            if ((episode - changedEpisode[i]) > α_episode) {
//              αStage[i] *= 0.9
//              changedEpisode[i] = episode
//            }
//          }
//          αStage[i] / numTilings
//        },
        episodes = max_episode,
        episodeListener = episodeListener,
        stepListener = stepListener
    )
    animate = true
    step_round = 1
    prob.Play(
        π = EpsilonGreedyFunctionPolicy(func, 0.0),
        episodes = max_episode,
        episodeListener = episodeListener,
        stepListener = stepListener
    )
  }
  D2DGameUI.apply {
    canvas_width = FlyPlane.fieldWidth
    canvas_height = FlyPlane.fieldWidth
    width = 1200.0
    height = 1000.0
    charts.addAll(AreaChartDescription("return per stage", "episode", "max return",
                                       numSeries = FlyPlane.maxStage, yForceZeroInRange = false),
                  AreaChartDescription("win rate per stage", "stage", "win rate per $episode_round episode",
                                       numSeries = FlyPlane.maxStage),
                  AreaChartDescription("number of visits per stage", "stage", "visits",
                                       numSeries = 1),
                  AreaChartDescription("probability of visiting stage", "stage", "probability",
                                       numSeries = 1),
                  LineChartDescription("number of features", "episode", "features",
                                       numSeries = 2, yForceZeroInRange = false),
                  LineChartDescription("speed of training", "time(s)", "speed(steps/s)",
                                       numSeries = 1, yForceZeroInRange = false),
                  AreaChartDescription("α per stage", "stage", "α",
                                       numSeries = FlyPlane.maxStage)
    )
    afterStartup = { gc ->
      gc.font = Font("Arial", 20.0)
      latch.countDown()
    }
  }
  Application.launch(D2DGameUI::class.java)
}