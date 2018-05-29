@file:Suppress("NOTHING_TO_INLINE")

package wumo.util

import java.util.concurrent.ThreadLocalRandom
import kotlin.math.pow
import kotlin.math.round

inline fun Rand() = ThreadLocalRandom.current()!!
fun Double.format(digits: Int): String {
  val base = 10.0.pow(digits)
  return String.format("%.${digits}f", round(this * base) / base)
}

fun <T> argmax_tie_random(set: Array<T>, evaluate: (T) -> Double): T {
  val iterator = set.iterator()
  val max_a = mutableListOf(iterator.next())
  var max = evaluate(max_a[0])
  while (iterator.hasNext()) {
    val tmp = iterator.next()
    val p = evaluate(tmp)
    if (p > max) {
      max = p
      max_a.apply {
        clear()
        add(tmp)
      }
    } else if (p == max)
      max_a.add(tmp)
  }
  return max_a[Rand().nextInt(max_a.size)]
}

fun <T> max(set: Iterable<T>, default: Double = Double.NaN, evaluate: T.(T) -> Double): Double {
  val iterator = set.iterator()
  if (!iterator.hasNext()) return default
  var tmp = iterator.next()
  var max = evaluate(tmp, tmp)
  while (iterator.hasNext()) {
    tmp = iterator.next()
    val p = evaluate(tmp, tmp)
    if (p > max)
      max = p
  }
  return max
}

fun <T> rand(set: Iterable<T>, evaluate: T.(T) -> Number): T {
  var size = 0
  val dem = set.sumByDouble { size++; evaluate(it, it).toDouble() }
  if (dem == 0.0) {
    val chosen = Rand().nextInt(size)
    for ((i, element) in set.withIndex())
      if (i == chosen) return element
    return set.first()
  }
  val p = Rand().nextDouble()
  var acc = 0.0
  for (element in set) {
    acc += evaluate(element, element).toDouble() / dem
    if (p <= acc)
      return element
  }
  throw IllegalArgumentException("random=$p, but accumulation=$acc")
}