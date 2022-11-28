package com.flyn.sarcopenia_project.viewer

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.file.cache_file.CacheFile
import com.flyn.sarcopenia_project.file.cache_file.EmgCacheFile
import com.flyn.sarcopenia_project.file.cache_file.ImuCacheFile
import com.flyn.sarcopenia_project.service.BluetoothLeService
import com.flyn.sarcopenia_project.utils.ActionManager
import com.flyn.sarcopenia_project.utils.ExtraManager
import com.flyn.sarcopenia_project.utils.FileManager
import com.flyn.sarcopenia_project.utils.TimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class FileRecordService: Service() {

    companion object {
        const val NOTIFICATION_ID = 1
    }

    private val dataReceiver = object: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            intent.getShortArrayExtra(ExtraManager.BLE_DATA)?.let { data ->
                when(intent.action) {
                    ActionManager.EMG_LEFT_DATA_AVAILABLE -> {
                        val file = EmgCacheFile(TimeManager.time, data)
                        writeFile(FileManager.EMG_LEFT_FILE_NAME, file)
                        dataCount[0] += 100
                    }
                    ActionManager.EMG_RIGHT_DATA_AVAILABLE -> {
                        val file = EmgCacheFile(TimeManager.time, data)
                        writeFile(FileManager.EMG_RIGHT_FILE_NAME, file)
                        dataCount[1] += 100
                    }
                    ActionManager.ACC_DATA_AVAILABLE -> {
                        val file = ImuCacheFile(TimeManager.time, data[0], data[1], data[2])
                        writeFile(FileManager.IMU_ACC_FILE_NAME, file)
                        dataCount[2]++
                    }
                    ActionManager.GYR_DATA_AVAILABLE -> {
                        val file = ImuCacheFile(TimeManager.time, data[0], data[1], data[2])
                        writeFile(FileManager.IMU_GYR_FILE_NAME, file)
                        dataCount[3]++
                    }
                    else -> return
                }
            }
        }

    }

    private val dataCount = mutableListOf(0, 0, 0, 0)

    private fun writeFile(fileName: String, file: CacheFile) {
        GlobalScope.launch(Dispatchers.IO) {
            FileManager.appendRecordData(fileName, file)
        }
    }

    private fun saveFile() {
        GlobalScope.launch(Dispatchers.IO) {
            FileManager.writeRecordFile(dataCount[0], dataCount[1], dataCount[2], dataCount[3])
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, R.string.sava_completed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.takeIf {
            it.getBooleanExtra(ExtraManager.NEED_SAVE_FILE, false)
        }?.let { saveFile() }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        IntentFilter().run {
            addAction(ActionManager.EMG_LEFT_DATA_AVAILABLE)
            addAction(ActionManager.EMG_RIGHT_DATA_AVAILABLE)
            addAction(ActionManager.ACC_DATA_AVAILABLE)
            addAction(ActionManager.GYR_DATA_AVAILABLE)
            registerReceiver(dataReceiver, this)
        }
        TimeManager.resetTime()



        val notification: Notification = Notification.Builder(this, "Sarcopenia project")
            .setContentTitle("Sarcopenia project")
            .setContentText("Data receiving")
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(dataReceiver)
        GlobalScope.launch(Dispatchers.IO) {
            FileManager.removeTempRecord()
        }
    }

}