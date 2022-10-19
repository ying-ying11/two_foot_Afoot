package com.flyn.sarcopenia_project.viewer

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.flyn.sarcopenia_project.MainActivity
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.file.cache_file.EmgCacheFile
import com.flyn.sarcopenia_project.file.cache_file.ImuCacheFile
import com.flyn.sarcopenia_project.service.BleAction
import com.flyn.sarcopenia_project.service.BluetoothLeService
import com.flyn.sarcopenia_project.utils.ExtraManager
import com.flyn.sarcopenia_project.utils.FileManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.min


class DataViewer: AppCompatActivity() {

    companion object {
        private const val TAG = "Data Viewer"
        private val tagName = listOf("EMG", "ACC", "GYR")
    }

    private val emg = DataPage(0f, 4096f, "Left", "Right") { "%.2f V".format(emgTransform(it)) }

    private val acc = DataPage(-32768f, 32768f, "x", "y", "z") { "%.2f g".format(accTransform(it)) }

    private val gyr = DataPage(-32768f, 32768f, "x", "y", "z") { "%.2f rad/s".format(gyrTransform(it)) }

    private val pageAdapter = object: FragmentStateAdapter(supportFragmentManager, lifecycle) {

        val fragments = arrayOf(emg, acc, gyr)

        override fun getItemCount(): Int = fragments.size

        override fun createFragment(position: Int): Fragment = fragments[position]

    }

    private val dataReceiver = object: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BleAction.GATT_CONNECTED.name -> {
                    service.enableNotification(true)
                }
                BleAction.GATT_DISCONNECTED.name -> {
                    // reconnect
                    GlobalScope.launch(Dispatchers.Default) {
                        while (!service.connect(address)) {
                            delay(100)
                        }
                    }
                }
                BleAction.EMG_LEFT_DATA_AVAILABLE.name -> {
                    intent.getShortArrayExtra(BluetoothLeService.DATA)?.let {
                        emgLeftData = it
                        emgState = emgState or 0x1
                        addEmgData()
                    }
                }
                BleAction.EMG_RIGHT_DATA_AVAILABLE.name -> {
                    intent.getShortArrayExtra(BluetoothLeService.DATA)?.let {
                        emgRightData = it
                        emgState = emgState or 0x2
                        addEmgData()
                    }
                }
                BleAction.ACC_DATA_AVAILABLE.name -> {
                    intent.getShortArrayExtra(BluetoothLeService.DATA)?.let { data ->
                        GlobalScope.launch(Dispatchers.IO) {
                            val file = ImuCacheFile(time, data[0], data[1], data[2])
                            FileManager.appendRecordData(FileManager.IMU_ACC_FILE_NAME, file)
                        }
                        val text =  data.map { accTransform(it) }.let {
                            getString(R.string.acc_describe, it[0], it[0], it[0])
                        }
                        acc.addData(text, data[0], data[1], data[2])
                        acc.updateSamplingRate(++accCount)
                    }
                }
                BleAction.GYR_DATA_AVAILABLE.name -> {
                    intent.getShortArrayExtra(BluetoothLeService.DATA)?.let { data ->
                        GlobalScope.launch(Dispatchers.IO) {
                            val file = ImuCacheFile(time, data[0], data[1], data[2])
                            FileManager.appendRecordData(FileManager.IMU_GYR_FILE_NAME, file)
                        }
                        val text = data.map { gyrTransform(it) }.let {
                            getString(R.string.gyr_describe, it[0], it[1], it[2])
                        }
                        gyr.addData(text, data[0], data[1], data[2])
                        gyr.updateSamplingRate(++gyrCount)
                    }
                }
            }
        }

    }

    private val serviceCallback = object: ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as BluetoothLeService.BleServiceBinder).getService()
            service.connect(address)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service.enableNotification(false)
        }

    }

    val time: Long
        get() = Date().time - startTime

    private val startTime = Date().time
    private val tabSelector: TabLayout by lazy { findViewById(R.id.data_viewer_tab) }
    private val pager: ViewPager2 by lazy { findViewById(R.id.data_viewer)}
    private val saveButton: Button by lazy { findViewById(R.id.data_viewer_save_button) }

    private lateinit var address: String
    private lateinit var service: BluetoothLeService
    private lateinit var emgLeftData: ShortArray
    private lateinit var emgRightData: ShortArray
    private var emgState: Int = 0
    private var emgLeftCount = 0
    private var emgRightCount = 0
    private var accCount = 0
    private var gyrCount = 0

    private fun emgTransform(value: Short): Float = value.toFloat() / 4095f * 3.6f
    private fun emgTransform(value: Float): Float = value / 4095f * 3.6f

    private fun accTransform(value: Short): Float = value.toFloat() / 32767f * 2f
    private fun accTransform(value: Float): Float = value / 32767f * 2f

    private fun gyrTransform(value: Short): Float = value.toFloat() / 32767f * 250f
    private fun gyrTransform(value: Float): Float = value / 32767f * 250f

    private fun addEmgData() {
        if (emgState != 0x3) return
        GlobalScope.launch(Dispatchers.IO) {
            FileManager.appendRecordData(FileManager.EMG_LEFT_FILE_NAME, EmgCacheFile(time, emgLeftData))
            FileManager.appendRecordData(FileManager.EMG_RIGHT_FILE_NAME, EmgCacheFile(time, emgRightData))
        }
        for (i in 0 until min(emgLeftData.size, emgRightData.size)) {
            val left = emgLeftData[i]
            val right = emgRightData[i]
            val text = getString(R.string.emg_describe, emgTransform(left), emgTransform(right))
            emg.addData(text, left, right)
        }
        emgLeftCount += emgLeftData.size
        emgRightCount += emgRightData.size
        emg.updateSamplingRate(min(emgLeftCount, emgRightCount))
        emgState = 0
    }

    private fun saveFile() {
        GlobalScope.launch(Dispatchers.IO) {
            FileManager.writeRecordFile(emgLeftCount, emgRightCount, accCount, gyrCount)
            this@DataViewer.runOnUiThread {
                Toast.makeText(this@DataViewer.applicationContext, R.string.sava_completed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_viewer)

        intent.getStringExtra(ExtraManager.DEVICE_ADDRESS)?.let {
            address = it
        }?:run {
            startActivity(Intent(this, MainActivity::class.java))
        }

        pager.adapter = pageAdapter
        TabLayoutMediator(tabSelector, pager) { tab, position ->
            tab.text = tagName[position]
        }.attach()

        saveButton.setOnClickListener {
            MaterialAlertDialogBuilder(this).apply {
                setMessage(R.string.check_saving)
                setPositiveButton(R.string.save) { dialog, which ->
                    saveFile()
                    startActivity(Intent(this@DataViewer, MainActivity::class.java))
                }
                setNegativeButton(R.string.cancel) { dialog, which ->
                    startActivity(Intent(this@DataViewer, MainActivity::class.java))
                }
            }.show()
        }

        IntentFilter().run {
            addAction(BleAction.GATT_CONNECTED.name)
            addAction(BleAction.GATT_DISCONNECTED.name)
            addAction(BleAction.EMG_LEFT_DATA_AVAILABLE.name)
            addAction(BleAction.EMG_RIGHT_DATA_AVAILABLE.name)
            addAction(BleAction.ACC_DATA_AVAILABLE.name)
            addAction(BleAction.GYR_DATA_AVAILABLE.name)
            registerReceiver(dataReceiver, this)
        }

        bindService(
            Intent(this, BluetoothLeService::class.java), serviceCallback,
            BIND_AUTO_CREATE)

    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(dataReceiver)
        unbindService(serviceCallback)
        GlobalScope.launch(Dispatchers.IO) {
            FileManager.removeTempRecord()
        }
    }

}