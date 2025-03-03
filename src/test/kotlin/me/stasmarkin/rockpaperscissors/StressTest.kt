package me.stasmarkin.rockpaperscissors

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.charset.Charset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random


private const val NUM_BOTS = 1_000

private val ioDispatcher = Dispatchers.IO.limitedParallelism(256)
private val botDispatcher = Dispatchers.Default.limitedParallelism(Runtime.getRuntime().availableProcessors() * 4)
private val threadLocalRnd = ThreadLocal.withInitial { Random(System.currentTimeMillis()) }
private val rnd: Random
  get() = threadLocalRnd.get()


fun main() = runBlocking {
  val jobs = List(NUM_BOTS) { id ->
    delay(5)
    launch(botDispatcher) { GameBot(id).play() }
  }

  jobs.forEach { it.join() }
}

class GameBot(
  private val id: Int,
) {
  private val log = LoggerFactory.getLogger("Bot-$id")
  private val messageChannel = Channel<String>(Channel.BUFFERED)

  private var channel: AsynchronousSocketChannel? = null
  private val readBuffer = ByteBuffer.allocateDirect(1024)
  private val writeBuffer = ByteBuffer.allocateDirect(1024)

  @Volatile
  private var currentState: State = State.INIT

  private enum class State {
    INIT,
    WAITING_FOR_GAME,
    IN_GAME,
    GAME_OVER
  }

  suspend fun play() {
    while (true) {
      connect()
      log.info("Connected")

      coroutineScope {
        val writes = launch(botDispatcher) { handleMessages() }
        val reads = launch(ioDispatcher) { startReading() }

        while (currentState != State.GAME_OVER) {
          delay(100)
        }

        reads.cancelAndJoin()
        writes.cancelAndJoin()
      }

      log.info("Game finished, disconnecting")
      disconnect()
      log.info("Reconnecting in 1 second")
      delay(1000)
    }
  }

  private suspend fun connect() = suspendCoroutine<Unit> { continuation ->
    try {
      val newChannel = AsynchronousSocketChannel.open()
      channel = newChannel

      newChannel.connect(InetSocketAddress("localhost", 8080), null, object : CompletionHandler<Void?, Nothing?> {
        override fun completed(result: Void?, attachment: Nothing?) {
          currentState = State.INIT
          continuation.resume(Unit)
        }

        override fun failed(exc: Throwable, attachment: Nothing?) {
          continuation.resumeWithException(exc)
        }
      })
    } catch (e: Exception) {
      continuation.resumeWithException(e)
    }
  }

  private suspend fun disconnect() {
    try {
      channel?.close()
      channel = null
    } catch (e: Exception) {
      log.error("Error during disconnect", e)
    }
  }

  private suspend fun startReading() {
    try {
      val asyncChannel = channel ?: return

      while (currentCoroutineContext().isActive) {
        readBuffer.clear()

        val bytesRead = suspendCoroutine<Int> { cont ->
          asyncChannel.read(readBuffer, null, object : CompletionHandler<Int, Nothing?> {
            override fun completed(result: Int, attachment: Nothing?) {
              cont.resume(result)
            }

            override fun failed(exc: Throwable, attachment: Nothing?) {
              cont.resumeWithException(exc)
            }
          })
        }

        if (bytesRead <= 0) break

        readBuffer.flip()
        val message = Charset.defaultCharset().decode(readBuffer).toString()
        message.lines().filter { it.isNotEmpty() }.forEach { line ->
          log.info("<< $line")
          messageChannel.send(line)
        }

        // Give other coroutines a chance to execute
        yield()
      }
    } catch (_: CancellationException) {
      // Normal cancellation
    } catch (e: Exception) {
      log.error("Reading error", e)
    }
  }

  private suspend fun sendCommand(command: String) {
    try {
      val asyncChannel = channel ?: return
      writeBuffer.clear()
      writeBuffer.put("$command\n".toByteArray())
      writeBuffer.flip()

      log.info(">> $command")

      suspendCoroutine<Unit> { cont ->
        asyncChannel.write(writeBuffer, null, object : CompletionHandler<Int, Nothing?> {
          override fun completed(result: Int, attachment: Nothing?) {
            if (writeBuffer.hasRemaining()) {
              // If not all bytes were written, write again
              asyncChannel.write(writeBuffer, null, this)
            } else {
              cont.resume(Unit)
            }
          }

          override fun failed(exc: Throwable, attachment: Nothing?) {
            cont.resumeWithException(exc)
          }
        })
      }
    } catch (e: Exception) {
      log.error("Write error", e)
    }
  }

  private suspend fun handleMessages() {
    try {
      for (message in messageChannel) {
        when {
          message.contains("enter your nickname", ignoreCase = true) -> {
            delay(rnd.nextLong(500, 2000))
            sendCommand("Bot-$id")
            currentState = State.WAITING_FOR_GAME
          }

          message.contains("your opponent is", ignoreCase = true) -> {
            currentState = State.IN_GAME
            delay(rnd.nextLong(3000, 5000))
            sendRandomMove()
          }

          message.contains("draw", ignoreCase = true) -> {
            if (currentState == State.IN_GAME) {
              delay(500)
              sendRandomMove()
            }
          }

          message.contains("Goodbye", ignoreCase = true) -> {
            currentState = State.GAME_OVER
            break
          }
        }
      }
    } catch (e: Exception) {
      log.error("Message handling error", e)
    }
  }

  private suspend fun sendRandomMove() {
    val moves = listOf("ROCK", "PAPER", "SCISSORS")
    val move = moves[rnd.nextInt(moves.size)]
    sendCommand(move)
  }
}
