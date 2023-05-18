package com.flyn.sarcopenia_project.net

import android.content.Context
import android.widget.Toast
import com.dtx804lab.dtx_netty_lib.message.FileMessage
import com.dtx804lab.dtx_netty_lib.message.KeyMessage
import com.dtx804lab.dtx_netty_lib.message.UUIDMessage
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

class ClientHandler(private val context: Context, private val files: Array<File>): ChannelInboundHandlerAdapter() {

    companion object {
        private const val bufferSize = 1024
        private val uuid = UUID.randomUUID()
        private val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        private val keyGen = KeyGenerator.getInstance("AES")

        init {
            keyGen.init(KeyMessage.KEY_LENGTH)
        }
    }

    private val buffer = ByteBuffer.allocateDirect(bufferSize)
    private val encryptBuffer = ByteBuffer.allocateDirect(bufferSize)

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.writeAndFlush(UUIDMessage.encoder(ctx, UUIDMessage(uuid)))
        files.forEachIndexed { index, file ->
            val key = keyGen.generateKey()
            cipher.init(Cipher.ENCRYPT_MODE, key, Client.ivSpec)
            ctx.writeAndFlush(KeyMessage.encoder(ctx, KeyMessage(key)))
            try {
                val channel = RandomAccessFile(file, "r").channel
                var isRemaining = true
                while (isRemaining) {
                    isRemaining = channel.read(buffer) == bufferSize
                    buffer.flip()

                    // encrypt
                    if (isRemaining) cipher.update(buffer, encryptBuffer)
                    else cipher.doFinal(buffer, encryptBuffer)
                    encryptBuffer.flip()

                    FileMessage(file.name, isRemaining, encryptBuffer).run {
                        ctx.writeAndFlush(FileMessage.encoder(ctx, this)).let {
                            if (!isRemaining && index == files.size - 1) {
                                it.addListener {
                                    GlobalScope.launch(Dispatchers.Main) {
                                        Toast.makeText(context, "${files.size} File save complete.", Toast.LENGTH_SHORT).show()
                                    }
                                    ctx.close()
                                }
                            }
                        }
                    }

                    buffer.clear()
                    encryptBuffer.clear()
                }
            } catch (exception: IOException) {
                exception.printStackTrace()
            }
        }
    }

}