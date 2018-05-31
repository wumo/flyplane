package wumo.algorithm

import wumo.model.Action
import wumo.model.State

class LinearTileCodingFunc(val feature: SuttonTileCoding, val weightInit: (Int) -> Double = { 0.0 }) {
  val w = DoubleArray(feature.numOfComponents, weightInit)
  operator fun invoke(s: State, a: Action<State>): Double {
    return w.innerProduct(feature(s, a))
  }
}