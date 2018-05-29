@file:Suppress("NOTHING_TO_INLINE", "OVERRIDE_BY_INLINE", "UNCHECKED_CAST")

package wumo.model

import org.slf4j.LoggerFactory

/**
 * <p>
 * Created on 2017-08-31.
 * </p>
 *
 * @author wumo
 */

interface MDP {
  val γ: Double
  val started: () -> State
}

interface State {
  val actions: Array<Action<State>>?
}

inline val State.isTerminal
  get() = !isNotTerminal

inline val State.isNotTerminal
  get() = actions?.any() ?: false

interface Action<out S : State> {
  val sample: () -> Possible<S>
}

open class Possible<out S : State>(val next: S, val reward: Double) {
  open operator fun component1() = next
  open operator fun component2() = reward
}

class DefaultMDP(override val γ: Double, override val started: () -> State) : MDP
class DefaultAction<out E, out S : State>(val value: E, override val sample: () -> Possible<S>) : Action<S>