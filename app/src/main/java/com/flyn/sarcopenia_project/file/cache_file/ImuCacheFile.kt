package com.flyn.sarcopenia_project.file.cache_file

import java.lang.StringBuilder

class ImuCacheFile(private val timeLabel: Long,
                   private val x: Short,
                   private val y: Short,
                   private val z: Short): CacheFile {

    override fun toCsv(): String {
        return StringBuilder().apply {
            this.append("$timeLabel, $x, $y, $z\n")
        }.toString()
    }

}