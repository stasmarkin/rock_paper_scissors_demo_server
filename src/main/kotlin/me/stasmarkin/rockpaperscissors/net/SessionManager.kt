package me.stasmarkin.rockpaperscissors.net

import com.google.inject.Inject
import com.google.inject.Singleton
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.stasmarkin.rockpaperscissors.game.Lobby
import me.stasmarkin.rockpaperscissors.utils.silently
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Singleton
@Sharable
class SessionManager @Inject constructor(
  private val lobby: Lobby
) : SimpleChannelInboundHandler<String>() {

  companion object {
    private val log = LoggerFactory.getLogger(SessionManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Default)
  }

  init {
    scope.launch {
      while (true) {
        log.info("Active sessions: " + activeSessions.size)
        delay(1000)
      }
    }
  }

  private val activeSessions = ConcurrentHashMap<ChannelHandlerContext, Session>()

  override fun channelActive(ctx: ChannelHandlerContext) {
    log.trace("Client connected: {}", ctx.channel().remoteAddress())
    val session = Session(ctx.channel(), lobby)
    activeSessions[ctx] = session
    session.start()
  }

  override fun channelRead0(ctx: ChannelHandlerContext, msgRaw: String) {
    val msg = msgRaw.trim()
    log.trace("Received message from {}: {}", ctx.channel().remoteAddress(), msg)
    val session = activeSessions[ctx]
    if (session == null) {
      ctx.writeAndFlush("You don't have active session. Please, reconnect!\n")
      ctx.close()
      return
    }

    session.onUserMessage(msg)
  }

  override fun channelInactive(ctx: ChannelHandlerContext) {
    log.trace("Client disconnected: {}", ctx.channel().remoteAddress())
    val session = activeSessions[ctx] ?: return
    silently { session.onUserDisconnected() }
    activeSessions.remove(ctx)
  }

  override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    log.warn("Channel error", cause)
    silently { ctx.writeAndFlush("Server error occurred, disconnecting\n") }
    channelInactive(ctx)
    silently { ctx.close() }
  }
}
