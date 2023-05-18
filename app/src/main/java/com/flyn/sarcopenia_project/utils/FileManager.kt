package com.flyn.sarcopenia_project.utils

import com.flyn.sarcopenia_project.file.cache_file.CacheFile
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock

object FileManager {

    const val ADC_FILE_NAME = "adc"

    private const val TAG = "FILE_MANAGER"
    private const val HEADER = "timestamp, ha, lt, m1, m5, arch, mh\n"

    private val dataFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale("zh", "tw"))
    private val lock = ReentrantLock()

    internal lateinit var APP_DIR: File
    internal lateinit var CACHE_DIR: File
    internal val RECORDING_DIR: File
        get() {
            val dir = File(APP_DIR, "sarcopenia_record")
            if (!dir.exists()) dir.mkdir()
            return dir
        }
    internal val TEMP_DIR: File
        get() {
            val dir = File(APP_DIR, "temp_record")
            if (!dir.exists()) dir.mkdir()
            return dir
        }

    fun appendRecordData(index: Int, fileName: String, data: CacheFile) {
        lock.lock()
        FileOutputStream(File(TEMP_DIR, "${fileName}_$index.csv"), true).use { out ->
            out.write(data.toCsv().toByteArray())
        }
        lock.unlock()
    }

    fun removeTempRecord() {
        lock.lock()
        TEMP_DIR.deleteRecursively()
        lock.unlock()
    }

    fun writeRecordFile(fileCount: Int, nickname: List<String> = listOf()) {
        lock.lock()
        val filePath = dataFormat.format(Date())
        for (i in 0 until fileCount) {
            val name = if (i < nickname.size) nickname[i] else i.toString()
            FileOutputStream(File(RECORDING_DIR, "${filePath}_${name}_raw.csv")).use { out ->
                val adcFile = RandomAccessFile(File(TEMP_DIR, "${ADC_FILE_NAME}_$i.csv"), "r")
                out.write(HEADER.toByteArray())
                val fill = arrayOf(", , , , , ,")
                var text = combineText(fill,
                    arrayOf(adcFile.readLine())
                )
                while (text != null) {
                    out.write(text.toByteArray())
                    text = combineText(fill,
                        arrayOf(adcFile.readLine())
                    )
                }
            }
        }
        lock.unlock()
    }

    private fun combineText(fill: Array<String>, str: Array<String?>): String? {
        if (str.all { it == null }) return null
        return str.mapIndexed { i, s ->
            s ?: fill[i]
        }.joinToString(separator = ", ", postfix = "\n")
    }

}