package com.flyn.sarcopenia_project.file.cache_file

import java.lang.StringBuilder

class EmgCacheFile(private val timeLabel: Long, private val emgData: ShortArray): CacheFile {

    override fun toCsv(): String {
        return StringBuilder().apply {
            emgData.forEach { data ->
                this.append("$timeLabel, $data\n")
            }
        }.toString()
    }

}