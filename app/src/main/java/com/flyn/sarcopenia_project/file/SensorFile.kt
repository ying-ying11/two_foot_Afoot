package com.flyn.sarcopenia_project.file

import java.io.File
import java.io.Serializable
import java.util.*

class SensorFile(file: File): Serializable, Comparable<SensorFile> {

    companion object {
        private val fileSizeScale = listOf("bytes", "KB", "MB", "GB", "TB")
    }

    val name: String = file.name
    val size = fileSizeFormatter(file.length())

    private val lastModify = Date(file.lastModified())

    private fun fileSizeFormatter(size: Long, scale: Int = 0): String {
        if (size < 1024) {
            return "$size ${fileSizeScale.getOrElse(scale) { "10^${3*scale} bytes" }}"
        }
        return fileSizeFormatter(size / 1024, scale + 1)
    }

    override fun compareTo(other: SensorFile): Int {
        return (lastModify.time - other.lastModify.time).toInt()
    }

}