package me.stasmarkin.rockpaperscissors

import com.google.inject.Guice
import kotlinx.coroutines.runBlocking
import me.stasmarkin.rockpaperscissors.net.NettyGameServer

fun main(): Unit = runBlocking {
  val injector = Guice.createInjector()
  val server = injector.getInstance(NettyGameServer::class.java)
  server.start()
}