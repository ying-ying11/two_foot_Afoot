package com.flyn.sarcopenia_project.net

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.netty.util.ReferenceCountUtil

class RawMsgEncoder: MessageToByteEncoder<RawMessage>() {

    override fun encode(ctx: ChannelHandlerContext, msg: RawMessage, out: ByteBuf) {
        with (msg) {
            out.writeByte(code.toInt())
            out.writeInt(message.readableBytes())
            out.writeBytes(message.copy())
            ReferenceCountUtil.release(message)
        }
    }

}