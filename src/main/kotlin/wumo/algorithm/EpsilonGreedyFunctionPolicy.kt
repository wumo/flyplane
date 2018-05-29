@file:Suppress("NOTHING_TO_INLINE")

package wumo.algorithm

import wumo.model.*
import wumo.util.Rand
import wumo.util.argmax_tie_random

class EpsilonGreedyFunctionPolicy(val q: LinearTileCodingFunc, val ε: Double = 0.1) {
  operator fun invoke(s: State): Action<State> {
    return if (Rand().nextDouble() < ε)
      s.actions!![Rand().nextInt(s.actions!!.size)]
    else
      argmax_tie_random(s.actions!!) { q(s, it) }
  }
}