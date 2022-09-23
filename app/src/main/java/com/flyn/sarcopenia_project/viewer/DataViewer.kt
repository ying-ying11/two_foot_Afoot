package com.flyn.sarcopenia_project.viewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.service.BleAction
import com.flyn.sarcopenia_project.service.BleCommand
import com.flyn.sarcopenia_project.service.BluetoothLeService
import com.flyn.sarcopenia_project.toHexString
import com.flyn.sarcopenia_project.toShortArray
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.material.snackbar.Snackbar
import kotlin.math.min


class DataViewer: AppCompatActivity() {

    companion object {
        private const val TAG = "Data Viewer"
        private val tagName = listOf("EMG", "ACC", "GYR")
        private val dataFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss'.csv'", Locale("zh", "tw"))
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
                BleAction.EMG_LEFT_DATA_AVAILABLE.name -> {
                    intent.getByteArrayExtra(BluetoothLeService.DATA)?.let {
                        emgLeftData = EmgDecoder.decode(it)
                        emgState = emgState or 0x1
                        addEmgData()
                    }
                }
                BleAction.EMG_RIGHT_DATA_AVAILABLE.name -> {
                    intent.getByteArrayExtra(BluetoothLeService.DATA)?.let {
                        emgRightData = EmgDecoder.decode(it)
                        emgState = emgState or 0x2
                        addEmgData()
                    }
                }
                BleAction.ACC_DATA_AVAILABLE.name -> {
                    intent.getByteArrayExtra(BluetoothLeService.DATA)?.let {
                        val x = it.toShortArray()[0]
                        val y = it.toShortArray()[1]
                        val z = it.toShortArray()[2]
                        val text = getString(R.string.acc_describe, accTransform(x), accTransform(y), accTransform(z))
                        acc.addData(text, x, y, z)
                    }
                }
                BleAction.GYR_DATA_AVAILABLE.name -> {
                    intent.getByteArrayExtra(BluetoothLeService.DATA)?.let {
                        val x = it.toShortArray()[0]
                        val y = it.toShortArray()[1]
                        val z = it.toShortArray()[2]
                        val text = getString(R.string.gyr_describe, gyrTransform(x), gyrTransform(y), gyrTransform(z))
                        gyr.addData(text, x, y, z)
                    }
                }
            }
        }

    }

    private val tabSelector: TabLayout by lazy { findViewById(R.id.data_viewer_tab) }
    private val pager: ViewPager2 by lazy { findViewById(R.id.data_viewer)}
    private val saveButton: FloatingActionButton by lazy { findViewById(R.id.data_viewer_save_button) }

    private lateinit var emgLeftData: ShortArray
    private lateinit var emgRightData: ShortArray
    private var emgState: Int = 0

    private fun emgTransform(value: Short): Float = value.toFloat() / 4095f * 3.6f
    private fun emgTransform(value: Float): Float = value / 4095f * 3.6f

    private fun accTransform(value: Short): Float = value.toFloat() / 32767f * 2f
    private fun accTransform(value: Float): Float = value / 32767f * 2f

    private fun gyrTransform(value: Short): Float = value.toFloat() / 32767f * 250f
    private fun gyrTransform(value: Float): Float = value / 32767f * 250f

    private fun addEmgData() {
        if (emgState != 0x3) return
        for (i in 0 until min(emgLeftData.size, emgRightData.size)) {
            val left = emgLeftData[i]
            val right = emgRightData[i]
            val text = getString(R.string.emg_describe, emgTransform(left), emgTransform(right))
            emg.addData(text, left, right)
        }
        emgState = 0
    }

    private suspend fun saveFile() {
        withContext(Dispatchers.IO) {
            val dir = File(filesDir, "record")
            if (!dir.exists()) dir.mkdir()
            val filePath = dataFormat.format(Date()).replace(":", "-")
            FileOutputStream(File(dir, filePath)).use { out ->
                emg.getData().apply {
                    out.write("EMG,%d\n".format(size).toByteArray())
                }.forEach { (time, data) ->
                    out.write("%d,%d\n".format(time, data[0]).toByteArray())
                }
                acc.getData().apply {
                    out.write("ACC,%d\n".format(size).toByteArray())
                }.forEach { (time, data) ->
                    out.write("%d,%d,%d,%d\n".format(time, data[0], data[1], data[2]).toByteArray())
                }
                gyr.getData().apply {
                    out.write("GYR,%d\n".format(size).toByteArray())
                }.forEach { (time, data) ->
                    out.write("%d,%d,%d,%d\n".format(time, data[0], data[1], data[2]).toByteArray())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_viewer)
        pager.adapter = pageAdapter
        TabLayoutMediator(tabSelector, pager) { tab, position ->
            tab.text = tagName[position]
        }.attach()

        saveButton.setOnClickListener {
            Snackbar.make(findViewById(R.id.data_viewer_layout), getString(R.string.check_saving), Snackbar.LENGTH_LONG).run {
                setAction("確認") {
                    GlobalScope.launch {
                        saveFile()
                    }
                }
                show()
            }
        }

        IntentFilter().run {
            addAction(BleAction.EMG_LEFT_DATA_AVAILABLE.name)
            addAction(BleAction.EMG_RIGHT_DATA_AVAILABLE.name)
            addAction(BleAction.ACC_DATA_AVAILABLE.name)
            addAction(BleAction.GYR_DATA_AVAILABLE.name)
            registerReceiver(dataReceiver, this)
        }

        sendBroadcast(Intent(BleCommand.NOTIFICATION_ON.name))
    }

    override fun onDestroy() {
        super.onDestroy()
        sendBroadcast(Intent(BleCommand.NOTIFICATION_OFF.name))
    }

}