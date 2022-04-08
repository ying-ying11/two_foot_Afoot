package com.flyn.sarcopenia_project.net

import android.content.Context
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import java.io.File
import java.net.InetSocketAddress
import java.util.*

object Client {

    private val uuid = UUID.randomUUID()

    fun transferFiles(context: Context, files: Array<File>, host: String = "localhost", port: Int = 8787) {
        val workerGroup = NioEventLoopGroup()
        try {
            val future = Bootstrap().apply {
                group(workerGroup)
                channel(NioSocketChannel::class.java)
                option(ChannelOption.SO_KEEPALIVE, true)
                handler(object: ChannelInitializer<NioSocketChannel>() {

                    override fun initChannel(ch: NioSocketChannel) {
                        ch.pipeline().addLast(RawMsgEncoder(), FileMsgEncoder(), ClientHandler(context, uuid, files))
                    }

                })
            }.connect(InetSocketAddress(host, port)).sync()
            future.channel().closeFuture().sync()
        } finally {
            workerGroup.shutdownGracefully()
        }
    }

}