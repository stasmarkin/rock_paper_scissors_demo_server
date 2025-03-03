package me.stasmarkin.rockpaperscissors.game

import com.google.inject.Singleton
import java.util.concurrent.atomic.AtomicReference

@Singleton
class Lobby {
  private val exchanger = AtomicReference<Game.Player>()

  fun requestGame(payer: Game.Player) {
    while (true) {
      val waitingPlayer = exchanger.getAndSet(null)
      if (waitingPlayer != null) {
        Game(waitingPlayer, payer).start()
        return
      }

      if (exchanger.compareAndSet(null, payer)) {
        return
      }
    }
  }

  fun unregister(player: Game.Player) {
    exchanger.compareAndSet(player, null)
  }
}
