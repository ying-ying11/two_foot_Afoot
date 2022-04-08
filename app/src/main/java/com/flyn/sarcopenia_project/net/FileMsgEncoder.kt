package com.flyn.sarcopenia_project.net

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageEncoder
import java.nio.charset.Charset

class FileMsgEncoder: MessageToMessageEncoder<FileMessage>() {

    companion object {
        private val charset = Charset.forName("UTF-8")
    }

    override fun encode(ctx: ChannelHandlerContext, msg: FileMessage, out: MutableList<Any>) {
        with (msg) {
            val buffer = ctx.alloc().buffer()
            // FILE_TRANSFER
            if (!isRemaining()) {
                buffer.writeLong(uuid.mostSignificantBits)
                buffer.writeLong(uuid.leastSignificantBits)
                buffer.writeInt(fileName.length)
                buffer.writeCharSequence(fileName, charset)
                buffer.writeBoolean(remaining)
                buffer.writeInt(data.limit())
                buffer.writeBytes(data)
                out.add(RawMessage(1, buffer))
            }
            // REMAINING_FILE
            else {
                buffer.writeBoolean(remaining)
                buffer.writeInt(data.limit())
                buffer.writeBytes(data)
                out.add(RawMessage(2, buffer))
            }
        }
    }

}