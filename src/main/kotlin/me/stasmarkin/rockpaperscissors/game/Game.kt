package me.stasmarkin.rockpaperscissors.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.stasmarkin.rockpaperscissors.utils.ActorStateMachine
import org.slf4j.LoggerFactory

class Game(
  private val player1: Player,
  private val player2: Player
) : ActorStateMachine<Game.Event, Game.SealedState>(scope) {

  companion object {
    private val log = LoggerFactory.getLogger(Game::class.java)
    private val scope = CoroutineScope(Dispatchers.Default)
  }

  override suspend fun initialState(): State {
    return Greetings()
  }

  suspend fun onMove(player: Player, move: Move) {
    if (!validate(player)) return
    sendEvent(Event.PlayerMoved(player, move))
  }

  suspend fun onLeave(player: Player) {
    if (!validate(player)) return
    sendEvent(Event.PlayerLeft(player))
  }

  private suspend fun validate(player: Player): Boolean {
    if (player == player1 || player == player2) return true

    log.warn("Player $player is not in the game")
    player.sendMessage("How da fuck did you get here?")
    return false
  }

  interface Player {
    val nickname: String
    suspend fun sendMessage(msg: String)
    suspend fun onGameFinished()
    suspend fun onGameStarted(game: Game)
  }

  sealed class Event {
    data class PlayerMoved(val player: Player, val move: Move) : Event()
    data class PlayerLeft(val player: Player) : Event()
  }

  enum class Move {
    ROCK, PAPER, SCISSORS;

    fun compare(that: Move): Int = if (this == that) 0 else
      ((this.ordinal - that.ordinal + size) % size % 2) * 2 - 1

    companion object {
      val size = Move.entries.size

      fun fromString(value: String): Move? = when (value.uppercase()) {
        "ROCK", "R" -> ROCK
        "PAPER", "P" -> PAPER
        "SCISSORS", "S" -> SCISSORS
        else -> null
      }
    }
  }

  sealed class SealedState : ActorStateMachine.State<Event, SealedState>

  abstract inner class State : SealedState() {
    override suspend fun next(event: Event): State {
      return when (event) {
        is Event.PlayerLeft -> {
          val winner = if (event.player == player1) player2 else player1
          winner.sendMessage("${event.player.nickname} left the game")
          Over(winner = winner, loser = event.player)
        }

        is Event.PlayerMoved -> onMove(event)
      }
    }

    open suspend fun onMove(event: Event.PlayerMoved): State = this
  }

  inner class Greetings : State() {
    override suspend fun start(): State {
      player1.onGameStarted(this@Game)
      player2.onGameStarted(this@Game)
      val player1Msg = "Your opponent is ${player2.nickname}! Game started!\n" +
          "Enter your move (ROCK/PAPER/SCISSORS or R/P/S):"
      val player2Msg = "Your opponent is ${player1.nickname}! Game started!\n" +
          "Enter your move (ROCK/PAPER/SCISSORS or R/P/S):"
      player1.sendMessage(player1Msg)
      player2.sendMessage(player2Msg)
      return WaitingForFirstMove()
    }
  }

  inner class WaitingForFirstMove : State() {
    override suspend fun onMove(event: Event.PlayerMoved): State {
      val first = event.player
      val second = if (first == player1) player2 else player1

      first.sendMessage("You made your move. Wait for ${second.nickname}.")
      second.sendMessage("${first.nickname} made his move. Enter your move:")

      return WaitingForSecondMove(event)
    }
  }

  inner class WaitingForSecondMove(private val firstMove: Event.PlayerMoved) : State() {
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun onMove(secondMove: Event.PlayerMoved): State {
      if (secondMove.player == firstMove.player) {
        firstMove.player.sendMessage("You already made your move. Wait for ${if (firstMove.player == player1) player2.nickname else player1.nickname}.")
        return this
      }

      val score = firstMove.move.compare(secondMove.move)
      if (score == 0) {
        player1.sendMessage("It's a draw! Enter your new move:")
        player2.sendMessage("It's a draw! Enter your new move:")
        return WaitingForFirstMove()
      }

      val winner = if (score > 0) firstMove.player else secondMove.player
      val loser = if (score > 0) secondMove.player else firstMove.player
      return Over(winner = winner, loser = loser)
    }
  }

  inner class Over(val winner: Player, val loser: Player) : State() {
    override suspend fun start(): State {
      winner.sendMessage("You win against ${loser.nickname}! Congratulations!")
      loser.sendMessage("You lose against ${winner.nickname}! Better luck next time!")
      player1.onGameFinished()
      player2.onGameFinished()
      return this
    }

    override fun isTerminal() = true
  }

}
