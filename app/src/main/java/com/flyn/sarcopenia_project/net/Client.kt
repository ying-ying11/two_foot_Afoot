package com.flyn.sarcopenia_project.net

import android.content.Context
import com.flyn.fc_message.base.RawMessageCodec
import com.flyn.fc_message.secure.AesCodec
import com.flyn.fc_message.secure.RsaCodec
import com.flyn.fc_message.secure.decodeHex
import io.github.cdimascio.dotenv.dotenv
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import java.io.File
import java.net.InetSocketAddress
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Client {

    private val publicKey: RSAPublicKey
    private val aesKeySpec: SecretKeySpec
    internal val ivSpec: IvParameterSpec

    init {
        val dotenv = dotenv {
            directory = "/assets"
            filename = "env"
        }
        publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(dotenv["PUBLIC_KEY"]?.decodeHex())) as RSAPublicKey
        aesKeySpec = SecretKeySpec(dotenv["SECRET_KEY"]?.decodeHex(), "AES")
        ivSpec = IvParameterSpec(dotenv["IV_PARAMETER"]?.decodeHex())
    }

    fun transferFiles(context: Context, files: Array<File>, host: String = "localhost", port: Int = 8787) {
        val workerGroup = NioEventLoopGroup()
        try {
            val future = Bootstrap().apply {
                group(workerGroup)
                channel(NioSocketChannel::class.java)
                option(ChannelOption.SO_KEEPALIVE, true)
                handler(object: ChannelInitializer<NioSocketChannel>() {

                    override fun initChannel(ch: NioSocketChannel) {
                        with (ch.pipeline()) {
                            addLast(LengthFieldBasedFrameDecoder(10240, 0, 2, 0, 2))
                            addLast(LengthFieldPrepender(2))
                            addLast(AesCodec(aesKeySpec, ivSpec))
                            addLast(RawMessageCodec())
                            addLast(RsaCodec(publicKey = publicKey))
                            addLast(ClientHandler(context, files))
                        }
                    }

                })
            }.connect(InetSocketAddress(host, port)).sync()
            future.channel().closeFuture().sync()
        } finally {
            workerGroup.shutdownGracefully()
        }
    }

}