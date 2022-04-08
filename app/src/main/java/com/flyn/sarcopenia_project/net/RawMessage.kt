package com.flyn.sarcopenia_project.net

import io.netty.buffer.ByteBuf

class RawMessage(val code: Byte, val message: ByteBuf)