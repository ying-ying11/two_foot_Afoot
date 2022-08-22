package com.flyn.sarcopenia_project.net

import android.content.Context
import android.widget.Toast
import com.flyn.fc_message.message.FileMessage
import com.flyn.fc_message.message.UUIDMessage
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

class ClientHandler(private val context: Context, private val uuid: UUID, private val files: Array<File>): ChannelInboundHandlerAdapter() {

    companion object {
        private const val bufferSize = 1024
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.writeAndFlush(UUIDMessage.encoder(ctx, UUIDMessage(uuid)))
        files.forEachIndexed { index, file ->
            try {
                val channel = RandomAccessFile(file, "r").channel
                var isRemaining = true
                while (isRemaining) {
                    val buffer = ByteBuffer.allocateDirect(bufferSize)
                    isRemaining = channel.read(buffer) == bufferSize
                    buffer.flip()
                    FileMessage(file.name, isRemaining, buffer).run {
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
                }
            } catch (exception: IOException) {
                exception.printStackTrace()
            }
        }
    }

}