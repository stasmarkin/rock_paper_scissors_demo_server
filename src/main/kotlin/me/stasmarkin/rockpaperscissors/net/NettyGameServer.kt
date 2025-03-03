package me.stasmarkin.rockpaperscissors.net

import com.google.inject.Inject
import com.google.inject.Singleton
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import me.stasmarkin.rockpaperscissors.utils.silently
import org.slf4j.LoggerFactory

@Singleton
class NettyGameServer @Inject constructor(
  private val sessionManager: SessionManager,
) {
  companion object {
    private val log = LoggerFactory.getLogger(NettyGameServer::class.java)
  }

  private val bossGroup = NioEventLoopGroup(1)
  private val workerGroup = NioEventLoopGroup()

  fun start(port: Int = 8080) {
    try {
      val bootstrap = ServerBootstrap()
      bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel::class.java)
        .childHandler(object : ChannelInitializer<SocketChannel>() {
          override fun initChannel(ch: SocketChannel) {
            ch.pipeline().apply {
              addLast(StringDecoder())
              addLast(StringEncoder())
              addLast(sessionManager)
            }
          }
        })
        .option(ChannelOption.SO_BACKLOG, 128)
        .childOption(ChannelOption.SO_KEEPALIVE, true)

      val future = bootstrap.bind(port).sync()
      log.info("Rock, Paper, Scissors server started on port $port")
      future.channel().closeFuture().sync()
    } finally {
      shutdown()
    }
  }

  private fun shutdown() {
    log.info("Shutting down server...")
    silently { workerGroup.shutdownGracefully() }
    silently { bossGroup.shutdownGracefully() }
  }
}
