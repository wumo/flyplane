@file:Suppress("UNCHECKED_CAST")

package wumo.experiment

import javafx.application.Application
import javafx.application.Platform
import javafx.scene.chart.XYChart
import javafx.scene.paint.Color
import javafx.scene.text.Font
import org.junit.Test
import wumo.algorithm.*
import wumo.model.DefaultAction
import wumo.problem.FlyPlane
import wumo.util.*
import java.awt.geom.Area
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.math.*

class `Test FlyPlane Problem with TD λ` {
  @Test
  fun test() {
    FlyPlane.maxStage = 100
    FlyPlane.numObstaclesPerStage = 30
    val resolution = 100
    val vel_resolution = 10
    val unit = FlyPlane.fieldWidth / resolution
    val m = ceil(sqrt(FlyPlane.maxStage.toDouble())).toInt()
    val stageWidth = FlyPlane.fieldWidth / m
    val stageScale = 1.0 / m
    fun Double.transX(stage: Int) = stageWidth * (stage % m) + this * stageScale
    fun Double.transY(stage: Int) = stageWidth * (stage / m) + this * stageScale
    fun Double.tranUnit() = this * stageScale
    
    val feature = SuttonTileCoding(numTilesPerTiling = 400000, _numTilings = 16, allowCollisions = true) { s, a, tilesFunc ->
      s as FlyPlane.PlaneState
      a as DefaultAction<Int, FlyPlane.PlaneState>
      val floats = ArrayList<Double>(FlyPlane.numObstaclesPerStage + 4).apply {
        add(s.loc.x / resolution);add(s.loc.y / resolution);add(s.vel.x / vel_resolution);add(s.vel.y / vel_resolution)
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
    val episode_round = 1000
    val step_round = 1000
    val max_episode = 20000000
    var episode_base = 0
    var animate = false
    val latch = CountDownLatch(1)
    thread(isDaemon = true) {
      latch.await()
      val ix = Math.floor(FlyPlane.plane.loc.x / unit).toInt()
      val iy = Math.floor(FlyPlane.plane.loc.y / unit).toInt()
      var oMax = Double.NEGATIVE_INFINITY
      var oMin = Double.POSITIVE_INFINITY
      val start = System.currentTimeMillis()
      var last = System.currentTimeMillis()
      var episodeSum = 0
      val episodeListener: EpisodeListener = { episode, step, st ->
        episodeSum++
        st as FlyPlane.PlaneState
        val current = System.currentTimeMillis()
        if (current - last > 5000) {
          val time = (current - start) / 1000.0
          val deltaTime = (current - last) / 1000.0
          val speed = episodeSum / deltaTime
          Platform.runLater {
            (D2DGameUI.charts[5] as LineChartDescription).data.apply {
              this[0].add(XYChart.Data(time.toInt(), speed.toInt()))
            }
          }
          episodeSum = 0
          last = current
        }
        D2DGameUI.render {
          D2DGameUI.title = "episode=$episode,total_step=$step\t,max=${oMax.format(2)},min=${oMin.format(2)}"
        }
        if (episode % episode_round == 0) {
          Platform.runLater {
            (D2DGameUI.charts[0] as LineChartDescription).data.apply {
              for (i in 0 until FlyPlane.maxStage) {
                val q = qvalue[i][ix][iy]
                if (q.isFinite())
                  this[i].add(XYChart.Data(episode + episode_base, q))
              }
            }
            (D2DGameUI.charts[1] as LineChartDescription).data.apply {
              for (i in 0 until FlyPlane.maxStage)
                this[i].add(XYChart.Data(episode + episode_base,
                                         if (FlyPlane.visitsPerStage[i] == 0) 0.0
                                         else
                                           FlyPlane.winsPerStage[i].toDouble() / FlyPlane.visitsPerStage[i]))
            }
//            (D2DGameUI.charts[2] as LineChartDescription).data.apply {
//              for (i in 0 until FlyPlane.maxStage)
//                this[i].add(XYChart.Data(episode + episode_base, FlyPlane.visitsPerStage[i]))
//            }
            (D2DGameUI.charts[2] as AreaChartDescription).data.apply {
              for (i in 0 until FlyPlane.maxStage)
                this[0].add(XYChart.Data(i, FlyPlane.visitsPerStage[i]))
            }
            (D2DGameUI.charts[3] as AreaChartDescription).data.apply {
              //              var sum = 0.0
//              for (w in FlyPlane.winsPerStage)
//                sum += exp(-w.toDouble())
              for (i in 0 until FlyPlane.maxStage)
                this[0].add(XYChart.Data(i, 1.0 / FlyPlane.maxStage))
            }
            (D2DGameUI.charts[4] as LineChartDescription).data.apply {
              this[0].add(XYChart.Data(episode + episode_base, feature.numOfComponents))
              this[1].add(XYChart.Data(episode + episode_base, feature.data.size))
            }
//            Thread.sleep(1000L)
          }
        }
      }
      val stepListener: StepListener = step@{ episode, step, s, a ->
        s as FlyPlane.PlaneState
        a as DefaultAction<Int, FlyPlane.PlaneState>
        
        val cx = floor(s.loc.x / unit).toInt()
        val cy = floor(s.loc.y / unit).toInt()
        if (s.stage != -1)
          qvalue[s.stage][cx][cy] = maxOf(qvalue[s.stage][cx][cy], func(s, a))
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
                gc.fill = Color.BLUE.interpolate(Color.RED, if (max == min) 0.5 else (q - min) / (max - min))
                gc.fillRect((nx * unit).transX(stage) - 1, (ny * unit).transY(stage) - 1, unit.tranUnit() + 2, unit.tranUnit() + 2)
              }
          }
          with(s) {
            for (stage in 0 until FlyPlane.maxStage) {
              gc.fill = Color.GREEN
              gc.fillOval((FlyPlane.target.loc.x - FlyPlane.target.radius).transX(stage),
                          (FlyPlane.target.loc.y - FlyPlane.target.radius).transY(stage),
                          2 * FlyPlane.target.radius.tranUnit(),
                          2 * FlyPlane.target.radius.tranUnit())
              gc.fill = Color.GREY
              for (i in 0 until FlyPlane.numObstaclesPerStage) {
                val obstacle = FlyPlane.stageObstacles[stage][i]
                val oLoc = obstacle.loc
                val oRadius = obstacle.radius
                gc.fillOval((oLoc.x - oRadius).transX(stage), (oLoc.y - oRadius).transY(stage),
                            2 * oRadius.tranUnit(), 2 * oRadius.tranUnit())
              }
            }
            gc.fill = Color.ORANGE
            val dir = s.vel.copy().norm() * FlyPlane.plane.radius
            val top = s.loc + dir
            val left = s.loc + (dir / 2.0).rot90L()
            val right = s.loc + (dir / 2.0).rot90R()
            gc.fillPolygon(doubleArrayOf(top.x.transX(stage), left.x.transX(stage), right.x.transX(stage)),
                           doubleArrayOf(top.y.transY(stage), left.y.transY(stage), right.y.transY(stage)),
                           3)
          }
          for (stage in 0 until FlyPlane.maxStage) {
            gc.stroke = Color.BLACK
            gc.strokeRect(0.0.transX(stage), 0.0.transY(stage), FlyPlane.fieldWidth.tranUnit(), FlyPlane.fieldWidth.tranUnit())
            gc.fill = Color.YELLOW
            gc.fillText("$stage", 10.0.transX(stage), 90.0.transY(stage))
          }
//          Thread.sleep((1.0 / 60 * 1000).toLong())
        }
      }
      val prob = FlyPlane.makeRand(minObstacleRadius = 0.0, maxObstacleRadius = 100.0)
      animate = false
      prob.`Specialized True Online Sarsa(λ)`(
          Qfunc = func,
          π = EpsilonGreedyFunctionPolicy(func, 0.1),
          λ = 0.96,
          α = 0.6 / numTilings,
          episodes = max_episode,
          episodeListener = episodeListener,
          stepListener = stepListener
      )
      animate = true
      for (stage in 0 until FlyPlane.maxStage)
        for (nx in 0..qvalue.lastIndex)
          for (ny in 0..qvalue[nx].lastIndex)
            qvalue[stage][nx][ny] = Double.NEGATIVE_INFINITY
      episode_base += max_episode
    }
    D2DGameUI.apply {
      canvas_width = FlyPlane.fieldWidth
      canvas_height = FlyPlane.fieldWidth
      width = 1200.0
      height = 1000.0
      charts.addAll(LineChartDescription("return per stage", "episode", "max return",
                                         numSeries = FlyPlane.maxStage, yForceZeroInRange = false),
                    LineChartDescription("win rate per stage", "episode", "wins",
                                         numSeries = FlyPlane.maxStage, yForceZeroInRange = false),
                    AreaChartDescription("number of visits per stage", "stage", "visits",
                                         numSeries = 1),
                    AreaChartDescription("probability of visiting stage", "stage", "probability",
                                         numSeries = 1),
                    LineChartDescription("number of features", "episode", "features",
                                         numSeries = 2, yForceZeroInRange = false),
                    LineChartDescription("speed of training", "time(s)", "speed(episodes/s)",
                                         numSeries = 1, yForceZeroInRange = false))
      afterStartup = { gc ->
        gc.font = Font("Arial", 20.0)
        latch.countDown()
      }
    }
    Application.launch(D2DGameUI::class.java)
  }
}