package me.stasmarkin.rockpaperscissors.utils

import io.netty.channel.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.channels.ClosedChannelException
import kotlin.coroutines.resumeWithException

suspend fun Channel.writeAndFlushSuspended(msg: String) {
  suspendCancellableCoroutine<Unit> { continuation ->
    if (!isActive) {
      continuation.resume(Unit) { _, _, _ -> }
      return@suspendCancellableCoroutine
    }

    writeAndFlush(msg + "\n").addListener { future ->
      if (future.isSuccess) {
        continuation.resume(Unit) { _, _, _ -> }
        return@addListener
      }

      when (future.cause()) {
        is ClosedChannelException -> continuation.resume(Unit) { _, _, _ -> }
        else -> continuation.resumeWithException(future.cause())
      }
    }
  }
}
