package wumo.util

import javafx.application.Application
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.chart.*
import javafx.scene.control.ScrollPane
import javafx.scene.layout.HBox
import javafx.scene.layout.TilePane
import javafx.stage.Stage
import wumo.util.resource.ResourceLoader
import java.util.concurrent.CyclicBarrier


sealed class ChartDescription(val title: String,
                              val xAxisLabel: String, val yAxisLabel: String,
                              val numSeries: Int = 1,
                              val xForceZeroInRange: Boolean = true,
                              val yForceZeroInRange: Boolean = true)

class LineChartDescription(title: String,
                           xAxisLabel: String, yAxisLabel: String,
                           numSeries: Int = 1,
                           xForceZeroInRange: Boolean = true,
                           yForceZeroInRange: Boolean = true) :
    ChartDescription(title, xAxisLabel, yAxisLabel, numSeries, xForceZeroInRange, yForceZeroInRange) {
  val data = Array(numSeries) { FXCollections.observableArrayList<XYChart.Data<Number, Number>>()!! }
}

class AreaChartDescription(title: String,
                           xAxisLabel: String, yAxisLabel: String,
                           numSeries: Int = 1,
                           xForceZeroInRange: Boolean = true,
                           yForceZeroInRange: Boolean = true) :
    ChartDescription(title, xAxisLabel, yAxisLabel, numSeries, xForceZeroInRange, yForceZeroInRange) {
  val data = Array(numSeries) { FXCollections.observableArrayList<XYChart.Data<Number, Number>>()!! }
}

class BarChartDescription(title: String,
                          xAxisLabel: String, yAxisLabel: String,
                          numSeries: Int = 1,
                          xForceZeroInRange: Boolean = true,
                          yForceZeroInRange: Boolean = true,
                          val isAutoRange: Boolean = true,
                          val lowerBound: Double = 0.0,
                          val upperBound: Double = 100.0,
                          val tickUnit: Double = 1.0) :
    ChartDescription(title, xAxisLabel, yAxisLabel, numSeries, xForceZeroInRange, yForceZeroInRange) {
  val data = Array(numSeries) { FXCollections.observableArrayList<XYChart.Data<String, Number>>()!! }
}


class D2DGameUI : Application() {
  
  
  lateinit var canvas: Canvas
  lateinit var primaryStage: Stage
  
  companion object {
    var width = 1000.0
    var height = 800.0
    var canvas_width = 600.0
    var canvas_height = 800.0
    var title = ""
    val charts = FXCollections.observableArrayList<ChartDescription>()!!
    var afterStartup: (GraphicsContext) -> Unit = {}
    lateinit var render: ((GraphicsContext) -> Unit) -> Unit
    
  }
  
  override fun start(ps: Stage?) {
    primaryStage = ps!!
    
    primaryStage.title = title
    val scrollPane = ScrollPane()
    val chartGroup = TilePane()
    scrollPane.content = chartGroup
    chartGroup.prefColumns = 2 //preferred columns
    for (c in charts) {
      val chart = when (c) {
        is LineChartDescription ->
          LineChart(NumberAxis().apply { label = c.xAxisLabel;isForceZeroInRange = c.xForceZeroInRange },
                    NumberAxis().apply { label = c.yAxisLabel;isForceZeroInRange = c.yForceZeroInRange },
                    FXCollections.observableArrayList<XYChart.Series<Number, Number>>().apply {
                      for ((i, d) in c.data.withIndex())
                        add(XYChart.Series("$i", d))
                    }).apply {
            title = c.title
            isLegendVisible = false
            createSymbols = false
            animated = true
            stylesheets.add(ResourceLoader.getResource("StockLineChart.css").toExternalForm())
          }
        is AreaChartDescription -> {
          AreaChart(NumberAxis().apply { label = c.xAxisLabel;isForceZeroInRange = c.xForceZeroInRange },
                    NumberAxis().apply { label = c.yAxisLabel;isForceZeroInRange = c.yForceZeroInRange },
                    FXCollections.observableArrayList<XYChart.Series<Number, Number>>().apply {
                      for ((i, d) in c.data.withIndex())
                        add(XYChart.Series("$i", d))
                    }).apply {
            title = c.title
            isLegendVisible = false
            createSymbols = false
            animated = true
            stylesheets.add(ResourceLoader.getResource("StockLineChart.css").toExternalForm())
          }
        }
        is BarChartDescription ->
          BarChart(CategoryAxis().apply { label = c.xAxisLabel },
                   NumberAxis().apply {
                     label = c.yAxisLabel;isForceZeroInRange = c.yForceZeroInRange
                     if (!c.isAutoRange) {
                       isAutoRanging = false
                       lowerBound = c.lowerBound
                       upperBound = c.upperBound
                       tickUnit = c.tickUnit
                     }
                   },
                   FXCollections.observableArrayList<XYChart.Series<String, Number>>().apply {
                     for ((i, d) in c.data.withIndex())
                       add(XYChart.Series("$i", d))
                   }).apply {
            title = c.title
            isLegendVisible = false
            animated = true
            stylesheets.add(ResourceLoader.getResource("StockLineChart.css").toExternalForm())
          }
      }
      chartGroup.children.add(chart)
    }
    val root = HBox()
    canvas = Canvas(canvas_width, canvas_height)
    root.children.addAll(canvas, scrollPane)
    primaryStage.scene = Scene(root, width, height)
    primaryStage.isMaximized = true
    primaryStage.show()
    render = this::render
    afterStartup(canvas.graphicsContext2D)
  }
  
  val barrier = CyclicBarrier(2)
  fun render(draw: (GraphicsContext) -> Unit = {}) {
//    barrier.reset()
    Platform.runLater {
      val gc = canvas.graphicsContext2D
      draw(gc)
      primaryStage.title = title
//      barrier.await()
    }
//    barrier.await()
  }
  
}