package me.stasmarkin.rockpaperscissors.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.CoroutineContext


abstract class ActorStateMachine<E : Any, S : ActorStateMachine.State<E, S>>(
  private val scope: CoroutineScope
) {
  @Volatile
  private lateinit var actor: SendChannel<E>
  private var actorsCtx: CoroutineContext? = null
  private lateinit var currentState: State<E, S>

  @OptIn(ObsoleteCoroutinesApi::class)
  fun start() {
    val actorInitLock = Mutex(true)

    actor = scope.actor {
      actorInitLock.lock()

      actorsCtx = this.coroutineContext
      currentState = initialState()
      currentState = start(currentState)

      for (event: E in channel) {
        handleEvent(event)
        if (currentState.isTerminal()) {
          break
        }
      }

      silently { this.cancel() }
      silently { channel.close() }
    }

    actorInitLock.unlock()
  }

  protected abstract suspend fun initialState(): S

  protected suspend fun sendEvent(event: E) {
    if (!this::actor.isInitialized) {
      throw IllegalStateException("Actor is not started")
    }

    // Check if we're in the same dispatcher/scope as the actor to avoid potential deadlocks
    val inSameContext = actorsCtx == currentCoroutineContext()
    if (inSameContext) {
      handleEvent(event)
      return
    }

    actor.send(event)
  }

  private suspend fun handleEvent(event: E) {
    val prevState = currentState
    currentState = prevState.next(event)
    if (prevState != currentState) {
      currentState = start(currentState)
    }
  }

  private suspend fun start(state: State<E, S>): State<E, S> {
    var result = state
    while (true) {
      val prev = result
      result = prev.start()
      if (prev == result) return result
    }
  }


  @Suppress("UNCHECKED_CAST")
  interface State<E : Any, S : State<E, S>> {
    suspend fun start(): S = this as S
    suspend fun next(event: E): S = this as S
    fun isTerminal(): Boolean = false
  }

}
