package com.flyn.sarcopenia_project.file.cache_file

import com.flyn.sarcopenia_project.utils.calibrate
import java.lang.StringBuilder

class FootPressureCacheFile(private val timeLabel: Long, private val footPressureData: ShortArray): CacheFile {

    override fun toCsv(): String {
        return StringBuilder().apply {
            this.append("$timeLabel")
            calibrate(footPressureData).forEach { data ->
                this.append(", $data")
            }
            this.append("\n")
        }.toString()
    }

}