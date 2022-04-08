package com.flyn.sarcopenia_project.net

import java.nio.ByteBuffer
import java.util.*

class FileMessage(val uuid: UUID, val fileName: String, val remaining: Boolean, val data: ByteBuffer) {

    companion object {
        val REMAINING_FILE_UUID = UUID(0, 0)
        const val REMAINING_FILE_NAME = "remaining"
    }

    fun isRemaining(): Boolean = (uuid == REMAINING_FILE_UUID && fileName == REMAINING_FILE_NAME)

}