package me.stasmarkin.rockpaperscissors.net

import io.netty.channel.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.stasmarkin.rockpaperscissors.game.Game
import me.stasmarkin.rockpaperscissors.game.Lobby
import me.stasmarkin.rockpaperscissors.utils.ActorStateMachine
import me.stasmarkin.rockpaperscissors.utils.silently
import me.stasmarkin.rockpaperscissors.utils.writeAndFlushSuspended
import org.slf4j.LoggerFactory

class Session(
  private val channel: Channel,
  private val lobby: Lobby,
) : ActorStateMachine<Session.Event, Session.State>(scope),
  Game.Player {

  companion object {
    private val log = LoggerFactory.getLogger(Session::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)
  }

  override val nickname: String
    get() = _nickname ?: throw IllegalStateException("Nickname not set")

  private var _nickname: String? = null


  override suspend fun initialState(): State = Welcome()


  fun onUserMessage(msg: String) {
    scope.launch { sendEvent(IncomingEvent.Input(msg)) }
  }

  fun onUserDisconnected() {
    scope.launch { sendEvent(SessionAction.Disconnect) }
  }

  override suspend fun sendMessage(msg: String) {
    sendEvent(SessionAction.Send(msg))
  }

  override suspend fun onGameStarted(game: Game) {
    sendEvent(IncomingEvent.GameStarted(game))
  }

  override suspend fun onGameFinished() {
    sendEvent(SessionAction.Disconnect)
  }


  sealed class Event

  sealed class IncomingEvent : Event() {
    data class Input(val msg: String) : IncomingEvent()
    data class GameStarted(val game: Game) : IncomingEvent()
  }

  sealed class SessionAction : Event() {
    data class Send(val msg: String) : SessionAction()
    data object Disconnect : SessionAction()
  }


  open inner class State : ActorStateMachine.State<Event, State> {
    override suspend fun start(): State = this

    override suspend fun next(event: Event): State = when (event) {
      is IncomingEvent -> handleInput(event)

      is SessionAction.Disconnect -> {
        silently { onDisconnect() }
        Over()
      }

      is SessionAction.Send -> try {
        channel.writeAndFlushSuspended(event.msg)
        this
      } catch (e: Exception) {
        log.warn("Failed to send message", e)
        silently { onDisconnect() }
        Over()
      }
    }

    open suspend fun onDisconnect(): Unit = Unit

    open suspend fun handleInput(event: IncomingEvent): State = this
  }


  inner class Welcome : State() {
    override suspend fun start(): State {
      sendMessage("Welcome to Rock, Paper, Scissors!")
      return NicknameInput()
    }
  }


  inner class NicknameInput : State() {
    override suspend fun start(): State {
      sendMessage("Please enter your nickname:")
      return this
    }

    override suspend fun handleInput(event: IncomingEvent): State = when (event) {
      is IncomingEvent.Input -> {
        val trimmedNickname = event.msg.trim()
        if (trimmedNickname.isEmpty()) {
          sendMessage("Nickname cannot be empty. Please try again:")
          this
        } else if (trimmedNickname.length > 20) {
          sendMessage("Nickname is too long (max 20 chars). Please try again:")
          this
        } else {
          _nickname = trimmedNickname
          sendMessage("Hello, $trimmedNickname! Entering lobby...")
          log.debug("Session started for player: $nickname")
          InLobby()
        }
      }

      is IncomingEvent.GameStarted -> this // Shouldn't happen in this state
    }
  }


  inner class InLobby : State() {
    override suspend fun start(): State {
      log.debug("Requesting game for player: $nickname")
      lobby.requestGame(this@Session)
      return this
    }

    override suspend fun handleInput(event: IncomingEvent): State = when (event) {
      is IncomingEvent.Input -> {
        if (event.msg == "q") {
          Over()
        } else {
          sendMessage("Waiting for opponent...")
          this
        }
      }

      is IncomingEvent.GameStarted -> InGame(event.game)
    }

    override suspend fun onDisconnect() {
      lobby.unregister(this@Session)
    }
  }


  inner class InGame(private val game: Game) : State() {
    override suspend fun handleInput(event: IncomingEvent): State = when (event) {
      is IncomingEvent.Input -> {
        val move = Game.Move.fromString(event.msg)
        if (move == null) {
          sendMessage("Invalid move")
        } else {
          game.onMove(this@Session, move)
        }
        this
      }

      is IncomingEvent.GameStarted -> this
    }

    override suspend fun onDisconnect() {
      game.onLeave(this@Session)
    }
  }


  inner class Over : State() {
    override suspend fun start(): State {
      try {
        sendMessage("Goodbye!")
        log.debug("Session ended for player: $_nickname")
      } catch (e: Exception) {
        log.warn("Failed to send goodbye message", e)
      } finally {
        silently {
          if (channel.isActive) {
            channel.close()
          }
        }
      }
      return this
    }

    override fun isTerminal() = true

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      return other is Over
    }

    override fun hashCode(): Int {
      return javaClass.hashCode()
    }
  }
}
