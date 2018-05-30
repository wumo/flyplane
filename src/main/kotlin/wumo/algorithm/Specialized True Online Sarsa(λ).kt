@file:Suppress("NOTHING_TO_INLINE")

package wumo.algorithm

import wumo.model.*

typealias EpisodeListener = (Int, Int, State) -> Unit

typealias StepListener = (Int, Int, State, Action<State>) -> Unit

inline fun HashMap<Int, Double>.scale(s: Double) {
  for (key in keys)
    compute(key) { _, v ->
      v!! * s
    }
}

inline fun HashMap<Int, Double>.scaleAdd(s: Double, x: IntArray) {
  for (i in x) {
    compute(i) { _, v ->
      (v ?: 0.0) + s
    }
  }
}

inline fun HashMap<Int, Double>.innerProduct(x: IntArray): Double {
  var sum = 0.0
  for (i in x)
    sum += this[i] ?: 0.0
  return sum
}

inline fun DoubleArray.innerProduct(x: IntArray): Double {
  var sum = 0.0
  for (i in x)
    sum += this[i]
  return sum
}

inline fun DoubleArray.scaleAdd(s: Double, z: HashMap<Int, Double>) {
  for ((i, v) in z.entries)
    this[i] += s * v
}

inline fun DoubleArray.scaleAdd(s: Double, x: IntArray) {
  for (i in x)
    this[i] += s
}

inline fun MDP.`Specialized True Online Sarsa(λ)`(
    Qfunc: LinearTileCodingFunc,
    π: EpsilonGreedyFunctionPolicy,
    λ: Double,
    α: (State, Int) -> Double,
    episodes: Int,
    maxStep: Int = Int.MAX_VALUE,
    episodeListener: EpisodeListener = { _, _, _ -> },
    stepListener: StepListener = { _, _, _, _ -> }) {
  
  val X = Qfunc.feature
  val w = Qfunc.w
  val z = HashMap<Int, Double>()
  for (episode in 0 until episodes) {
    println("$episode/$episodes")
    var step = 0
    var s = started()
    var a = π(s)
    var x = X(s, a)
    z.clear()
    var Q_old = 0.0
    val α = α(s, episode)
    while (true) {
      stepListener(episode, step, s, a)
      step++
      if (s.isTerminal || step >= maxStep) break
      val (s_next, reward) = a.sample()
      
      val tmp1 = (1.0 - α * γ * λ * z.innerProduct(x))
      z.scale(γ * λ)
      z.scaleAdd(tmp1, x)
      
      val Q = w.innerProduct(x)
      var δ = reward - Q
      if (s_next.isNotTerminal) {
        val a_next = π(s_next)
        val `x'` = X(s_next, a_next)
        val `Q'` = w.innerProduct(`x'`)
        δ += γ * `Q'`
        w.scaleAdd(α * (δ + Q - Q_old), z)
        w.scaleAdd(-α * (Q - Q_old), x)
        Q_old = `Q'`
        x = `x'`
        a = a_next
      } else {
        w.scaleAdd(α * (δ + Q - Q_old), z)
        w.scaleAdd(-α * (Q - Q_old), x)
      }
      s = s_next
    }
    episodeListener(episode, step, s)
  }
}

fun MDP.Play(
    π: EpsilonGreedyFunctionPolicy,
    episodes: Int,
    maxStep: Int = Int.MAX_VALUE,
    episodeListener: EpisodeListener = { _, _, _ -> },
    stepListener: StepListener = { _, _, _, _ -> }) {
  for (episode in 0 until episodes) {
    println("$episode/$episodes")
    var step = 0
    var s = started()
    var a = π(s)
    while (true) {
      stepListener(episode, step, s, a)
      step++
      if (s.isTerminal || step >= maxStep) break
      val (s_next) = a.sample()
      if (s_next.isNotTerminal)
        a = π(s_next)
      s = s_next
    }
    episodeListener(episode, step, s)
  }
}