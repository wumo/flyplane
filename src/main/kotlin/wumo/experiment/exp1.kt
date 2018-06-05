@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package wumo.experiment

import javafx.application.Application
import javafx.application.Platform
import javafx.scene.chart.XYChart
import javafx.scene.paint.Color
import javafx.scene.text.Font
import wumo.algorithm.*
import wumo.model.DefaultAction
import wumo.problem.FlyPlane
import wumo.util.*
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
var initialWeight = { 0.0 }

inline fun setting_100_30_demo() {
  FlyPlane.maxScene = 100
  FlyPlane.numObstaclesPerScene = 30
  maxObstacleRadius = 40.0
  
  numTilesPerTiling = 20_0000
  _numTilings = 8
  episode_round = 1000
  step_round = 100
  max_episode = 20_0000
}

inline fun setting_100_30_weight() {
  FlyPlane.maxScene = 100
  FlyPlane.numObstaclesPerScene = 30
  maxObstacleRadius = 40.0
  
  numTilesPerTiling = 20_0000
  _numTilings = 8
  episode_round = 1000
  step_round = 100
  max_episode = 200_0000
  initialWeight = { Rand().nextDouble(-1.0, 1.0) * 1000.0 }
}

inline fun setting_100_30_small() {
  FlyPlane.maxScene = 100
  FlyPlane.numObstaclesPerScene = 30
  maxObstacleRadius = 40.0
  
  numTilesPerTiling = 1_0000
  _numTilings = 8
  episode_round = 1000
  step_round = 100
  max_episode = 200_0000
}

inline fun setting_100_30() {
  FlyPlane.maxScene = 100
  FlyPlane.numObstaclesPerScene = 30
  maxObstacleRadius = 50.0
  
  numTilesPerTiling = 8_0000
  _numTilings = 16
  episode_round = 100
  step_round = 100
  max_episode = 2000_0000
}

inline fun setting_10_30() {
  FlyPlane.maxScene = 10
  FlyPlane.numObstaclesPerScene = 30
  numTilesPerTiling = 2_0000
  _numTilings = 16
  episode_round = 100
  step_round = 100
}

inline fun setting_10_10() {
  FlyPlane.maxScene = 10
  FlyPlane.numObstaclesPerScene = 10
  numTilesPerTiling = 2_0000
  _numTilings = 16
  episode_round = 100
  step_round = 100
}

inline fun setting_1_6() {
  FlyPlane.maxScene = 10
  FlyPlane.numObstaclesPerScene = 6
  numTilesPerTiling = 1_0000
  _numTilings = 16
  episode_round = 100
  step_round = 100
}

inline fun setting_2_30() {
  FlyPlane.maxScene = 2
  FlyPlane.numObstaclesPerScene = 30
  maxObstacleRadius = 50.0
  
  numTilesPerTiling = 4_0000
  _numTilings = 16
  episode_round = 100
  step_round = 100
  max_episode = 2_0000
}

inline fun test_setting() {
  FlyPlane.maxScene = 2
  FlyPlane.numObstaclesPerScene = 30
  maxObstacleRadius = 40.0
  
  numTilesPerTiling = 1_0000
  _numTilings = 8
  episode_round = 1000
  step_round = 100
  max_episode = 2_0000
}

inline fun custom_setting() {
  FlyPlane.maxScene = 10
  FlyPlane.numObstaclesPerScene = 30
  numTilesPerTiling = 4_0000
  _numTilings = 16
  episode_round = 100
  step_round = 100
}

fun main(args: Array<String>) {
  setting_100_30_demo()
//  setting_100_30_small()
//  setting_100_30()
//  setting_10_10()
//  setting_1_6()
//  setting_2_30()
//  custom_setting()
//  test_setting()
  val resolution = 100
  val unit = FlyPlane.fieldWidth / resolution
  val m = ceil(sqrt(FlyPlane.maxScene.toDouble())).toInt()
  val stageWidth = FlyPlane.fieldWidth / m
  val stageScale = 1.0 / m
  fun Double.transX(stage: Int) = stageWidth * (stage % m) + this * stageScale
  fun Double.transY(stage: Int) = stageWidth * (stage / m) + this * stageScale
  fun Double.tranUnit() = this * stageScale
  val feature = SuttonTileCoding(numTilesPerTiling = numTilesPerTiling, _numTilings = _numTilings, allowCollisions = true) { s, a, tilesFunc ->
    s as FlyPlane.PlaneState
    a as DefaultAction<Int, FlyPlane.PlaneState>
    val floats = ArrayList<Double>(FlyPlane.numObstaclesPerScene + 4).apply {
      add(s.loc.x / resolution);add(s.loc.y / resolution);add(s.vel.x);add(s.vel.y / PI * 18)
      for (i in 0 until FlyPlane.numObstaclesPerScene) {
        val obstacle = FlyPlane.sceneObstacles[s.scene][i]
        add(obstacle.loc.x / resolution);add(obstacle.loc.y / resolution);add(obstacle.radius / resolution)
      }
    }.toDoubleArray()
    tilesFunc(floats, intArrayOf(a.value))
  }
  
  val func = LinearTileCodingFunc(feature) { initialWeight() }
  val numTilings = feature.numTilings
  
  val qvalue = Array(FlyPlane.maxScene) {
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
    var episodeSum = 0
    val preWinsPerScene = IntArray(FlyPlane.maxScene)
    val winRate = DoubleArray(FlyPlane.maxScene)
    val preVisitsPerScene = IntArray(FlyPlane.maxScene)
    val αScene = DoubleArray(FlyPlane.maxScene) { _α }
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
      if (episode % episode_round == 0) {
        Platform.runLater {
          (D2DGameUI.charts[0] as AreaChartDescription).data.apply {
            if (this[0].isEmpty())
              for (i in 0 until FlyPlane.maxScene) {
                var q = qvalue[i][ix][iy]
                if (q.isInfinite()) q = 0.0
                this[0].add(XYChart.Data(i, q))
              }
            else
              for (i in 0 until FlyPlane.maxScene) {
                var q = qvalue[i][ix][iy]
                if (q.isInfinite()) q = 0.0
                this[0][i].yValue = q
              }
          }
          (D2DGameUI.charts[1] as AreaChartDescription).data.apply {
            if (this[0].isEmpty())
              for (i in 0 until FlyPlane.maxScene) {
                val visits = maxOf(FlyPlane.visitsPerScene[i] - preVisitsPerScene[i], 1)
                winRate[i] = (FlyPlane.winsPerScene[i] - preWinsPerScene[i]) / visits.toDouble()
                this[0].add(XYChart.Data(i, winRate[i]))
                preWinsPerScene[i] = FlyPlane.winsPerScene[i]
              }
            else
              for (i in 0 until FlyPlane.maxScene) {
                val visits = maxOf(FlyPlane.visitsPerScene[i] - preVisitsPerScene[i], 1)
                winRate[i] = (FlyPlane.winsPerScene[i] - preWinsPerScene[i]) / visits.toDouble()
                this[0][i].yValue = winRate[i]
                preWinsPerScene[i] = FlyPlane.winsPerScene[i]
              }
          }
          (D2DGameUI.charts[2] as AreaChartDescription).data.apply {
            System.arraycopy(FlyPlane.visitsPerScene, 0, preVisitsPerScene, 0, FlyPlane.maxScene)
            if (this[0].isEmpty())
              for (i in 0 until FlyPlane.maxScene)
                this[0].add(XYChart.Data(i, FlyPlane.visitsPerScene[i]))
            else
              for (i in 0 until FlyPlane.maxScene)
                this[0][i].yValue = FlyPlane.visitsPerScene[i]
          }
          (D2DGameUI.charts[3] as AreaChartDescription).data.apply {
            var sum = 0
            for (i in 0 until FlyPlane.maxScene)
              sum += FlyPlane.visitsPerScene[i]
            val max = wumo.util.max(0 until FlyPlane.maxScene) { FlyPlane.visitsPerScene[it].toDouble() } + 1
            val base = max * FlyPlane.maxScene - sum
            if (this[0].isEmpty())
              for (i in 0 until FlyPlane.maxScene)
                this[0].add(XYChart.Data(i, (max - FlyPlane.visitsPerScene[i]) / base))
            else
              for (i in 0 until FlyPlane.maxScene)
                this[0][i].yValue = (max - FlyPlane.visitsPerScene[i]) / base
          }
          (D2DGameUI.charts[4] as LineChartDescription).data.apply {
            this[0].add(XYChart.Data(episode, feature.numOfComponents))
            this[1].add(XYChart.Data(episode, feature.data.size))
          }
          (D2DGameUI.charts[6] as AreaChartDescription).data.apply {
            if (this[0].isEmpty())
              for (i in 0 until FlyPlane.maxScene)
                this[0].add(XYChart.Data(i, αScene[i]))
            else
              for (i in 0 until FlyPlane.maxScene)
                this[0][i].yValue = αScene[i]
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
      if (s.scene != -1) {
        qvalue[s.scene][cx][cy] = func(s, a)
//        qvalue[s.scene][cx][cy] = maxOf(qvalue[s.scene][cx][cy], func(s, a))
        mMax = maxOf(mMax, qvalue[s.scene][cx][cy])
        mMin = minOf(mMin, qvalue[s.scene][cx][cy])
      }
      if (episode % step_round != 0) return@step
      if (!animate && step > 1) return@step
      D2DGameUI.render { gc ->
        gc.clearRect(0.0, 0.0, FlyPlane.fieldWidth, FlyPlane.fieldWidth)
        gc.lineWidth = 3.0
        oMax = Double.NEGATIVE_INFINITY
        oMin = Double.POSITIVE_INFINITY
        for (scene in 0 until FlyPlane.maxScene) {
          var max = Double.NEGATIVE_INFINITY
          var min = Double.POSITIVE_INFINITY
          for (nx in 0 until resolution)
            for (ny in 0 until resolution) {
              val q = qvalue[scene][nx][ny]
              if (q.isInfinite()) continue
              max = maxOf(max, q)
              min = minOf(min, q)
            }
          oMax = maxOf(oMax, max)
          oMin = minOf(oMin, min)
          for (nx in 0 until resolution)
            for (ny in 0 until resolution) {
              var q = qvalue[scene][nx][ny]
              if (q.isInfinite())
                q = min
              //draw Q value
              gc.fill = Color.BLUE.interpolate(Color.RED, if (max == min) 0.5 else (q - min) / (max - min))
              gc.fillRect((nx * unit).transX(scene) - 1, (ny * unit).transY(scene) - 1, unit.tranUnit() + 2, unit.tranUnit() + 2)
            }
          //draw target per scene
          gc.fill = Color.GREEN
          gc.fillOval((FlyPlane.target.loc.x - FlyPlane.target.radius).transX(scene),
                      (FlyPlane.target.loc.y - FlyPlane.target.radius).transY(scene),
                      2 * FlyPlane.target.radius.tranUnit(),
                      2 * FlyPlane.target.radius.tranUnit())
          //draw obstacles per scene
          gc.fill = Color.GREY
          for (i in 0 until FlyPlane.numObstaclesPerScene) {
            val obstacle = FlyPlane.sceneObstacles[scene][i]
            val oLoc = obstacle.loc
            val oRadius = obstacle.radius
            gc.fillOval((oLoc.x - oRadius).transX(scene), (oLoc.y - oRadius).transY(scene),
                        2 * oRadius.tranUnit(), 2 * oRadius.tranUnit())
          }
          //draw edge per scene
          gc.stroke = Color.BLACK
          gc.strokeRect(0.0.transX(scene), 0.0.transY(scene), FlyPlane.fieldWidth.tranUnit(), FlyPlane.fieldWidth.tranUnit())
          gc.fill = Color.YELLOW
          gc.fillText("$scene", 10.0.transX(scene), 90.0.transY(scene))
        }
        with(s) {
          gc.fill = Color.ORANGE
          val vel = Vector2(s.vel.x * cos(s.vel.y), s.vel.x * sin(s.vel.y))
          val dir = vel.norm() * FlyPlane.plane.radius
          val top = s.loc + dir
          val left = s.loc + (dir / 2.0).rot90L()
          val right = s.loc + (dir / 2.0).rot90R()
          gc.fillPolygon(doubleArrayOf(top.x.transX(scene), left.x.transX(scene), right.x.transX(scene)),
                         doubleArrayOf(top.y.transY(scene), left.y.transY(scene), right.y.transY(scene)),
                         3)
        }
      }
      if (animate)
        Thread.sleep((1.0 / 10 * 1000).toLong())
    }
    val prob = FlyPlane.makeRand(maxObstacleRadius)
//    val prob = FlyPlane.makeSpecific()
    animate = false
    FlyPlane.determinedStartScene = -1
    prob.`Specialized True Online Sarsa(λ)`(
        Qfunc = func,
        π = EpsilonGreedyFunctionPolicy(func, ε),
        λ = 0.96,
        α = { _, _ -> _α / numTilings },
        episodes = max_episode,
        episodeListener = episodeListener,
        stepListener = stepListener
    )
    animate = true
    step_round = 1
    FlyPlane.determinedStartScene = 0
    prob.Play(
        π = EpsilonGreedyFunctionPolicy(func, 0.0),
        episodes = FlyPlane.maxScene,
        episodeListener = { _, _, _ ->
          FlyPlane.determinedStartScene++
        },
        stepListener = stepListener
    )
  }
  D2DGameUI.apply {
    canvas_width = FlyPlane.fieldWidth
    canvas_height = FlyPlane.fieldWidth
    width = 1200.0
    height = 1000.0
    charts.addAll(AreaChartDescription("每个场景的回报", "场景", "回报",
                                       numSeries = FlyPlane.maxScene, yForceZeroInRange = false),
                  AreaChartDescription("每${episode_round}回合到达终点的比率", "场景", "到达终点的比率",
                                       numSeries = FlyPlane.maxScene),
                  AreaChartDescription("每个场景的训练次数", "场景", "次数",
                                       numSeries = 1),
                  AreaChartDescription("每个场景的训练概率", "场景", "概率",
                                       numSeries = 1),
                  LineChartDescription("特征数量", "回合", "特征数",
                                       numSeries = 2, yForceZeroInRange = false),
                  LineChartDescription("仿真训练速度", "时间（秒）", "速度(场景数/秒)",
                                       numSeries = 1, yForceZeroInRange = false),
                  AreaChartDescription("每个场景的α值", "场景", "α",
                                       numSeries = FlyPlane.maxScene)
    )
    afterStartup = { gc ->
      gc.font = Font("Arial", 20.0)
      latch.countDown()
    }
  }
  Application.launch(D2DGameUI::class.java)
}