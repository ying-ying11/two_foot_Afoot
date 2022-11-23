package com.flyn.sarcopenia_project.viewer

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.flyn.sarcopenia_project.MainActivity
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.service.BluetoothLeService
import com.flyn.sarcopenia_project.utils.ActionManager
import com.flyn.sarcopenia_project.utils.ExtraManager
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
        private val dataFilter = IntentFilter().apply {
            addAction(ActionManager.EMG_LEFT_DATA_AVAILABLE)
            addAction(ActionManager.EMG_RIGHT_DATA_AVAILABLE)
            addAction(ActionManager.ACC_DATA_AVAILABLE)
            addAction(ActionManager.GYR_DATA_AVAILABLE)
        }
        private val connectFilter = IntentFilter().apply {
            addAction(ActionManager.GATT_CONNECTED)
            addAction(ActionManager.GATT_DISCONNECTED)
        }
    }

    private val emg = DataPage(0f, 4096f, "Left", "Right") { "%.2f V".format(emgTransform(it)) }

    private val acc = DataPage(-32768f, 32768f, "x", "y", "z") { "%.2f g".format(accTransform(it)) }

    private val gyr = DataPage(-32768f, 32768f, "x", "y", "z") { "%.2f rad/s".format(gyrTransform(it)) }

    private val pageAdapter = object: FragmentStateAdapter(supportFragmentManager, lifecycle) {

        val fragments = arrayOf(emg, acc, gyr)

        override fun getItemCount(): Int = fragments.size

        override fun createFragment(position: Int): Fragment = fragments[position]

    }

    private val connectReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                ActionManager.GATT_CONNECTED -> service.enableNotification(true)
                ActionManager.GATT_DISCONNECTED -> {
                    // reconnect
                    GlobalScope.launch(Dispatchers.Default) {
                        while (!service.connect(address)) {
                            delay(100)
                        }
                    }
                }
            }
        }

    }

    private val dataReceiver = object: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ActionManager.EMG_LEFT_DATA_AVAILABLE -> {
                    intent.getShortArrayExtra(BluetoothLeService.DATA)?.let {
                        emgLeftData = it
                        emgState = emgState or 0x1
                        addEmgData()
                    }
                }
                ActionManager.EMG_RIGHT_DATA_AVAILABLE -> {
                    intent.getShortArrayExtra(BluetoothLeService.DATA)?.let {
                        emgRightData = it
                        emgState = emgState or 0x2
                        addEmgData()
                    }
                }
                ActionManager.ACC_DATA_AVAILABLE -> {
                    intent.getShortArrayExtra(BluetoothLeService.DATA)?.let { data ->
                        val text =  data.map { accTransform(it) }.let {
                            getString(R.string.acc_describe, it[0], it[0], it[0])
                        }
                        acc.addDataCount(1)
                        acc.addData(text, data[0], data[1], data[2])
                    }
                }
                ActionManager.GYR_DATA_AVAILABLE -> {
                    intent.getShortArrayExtra(BluetoothLeService.DATA)?.let { data ->
                        val text = data.map { gyrTransform(it) }.let {
                            getString(R.string.gyr_describe, it[0], it[1], it[2])
                        }
                        gyr.addDataCount(1)
                        gyr.addData(text, data[0], data[1], data[2])
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

    private val tabSelector: TabLayout by lazy { findViewById(R.id.data_viewer_tab) }
    private val pager: ViewPager2 by lazy { findViewById(R.id.data_viewer)}
    private val saveButton: Button by lazy { findViewById(R.id.data_viewer_save_button) }
    private val finishDialog by lazy {
        MaterialAlertDialogBuilder(this).apply {
            setMessage(R.string.check_saving)
            setPositiveButton(R.string.save) { _, _ ->
                Intent(this@DataViewer, FileRecordService::class.java).run {
                    putExtra(ExtraManager.NEED_SAVE_FILE, true)
                    startService(this)
                }
                stopService(Intent(this@DataViewer, FileRecordService::class.java))
                startActivity(Intent(this@DataViewer, MainActivity::class.java))
                finish()
            }
            setNegativeButton(R.string.cancel) { _, _ ->
                stopService(Intent(this@DataViewer, FileRecordService::class.java))
                startActivity(Intent(this@DataViewer, MainActivity::class.java))
                finish()
            }
        }
    }

    private lateinit var address: String
    private lateinit var service: BluetoothLeService
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
        val len = min(emgLeftData.size, emgRightData.size)
        emg.addDataCount(len)
        for (i in 0 until len) {
            val left = emgLeftData[i]
            val right = emgRightData[i]
            val text = getString(R.string.emg_describe, emgTransform(left), emgTransform(right))
            emg.addData(text, left, right)
        }
        emgState = 0
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
            finishDialog.show()
        }

        registerReceiver(connectReceiver, connectFilter)

        bindService(
            Intent(this, BluetoothLeService::class.java), serviceCallback,
            BIND_AUTO_CREATE
        )

        startService(Intent(this, FileRecordService::class.java))

    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(connectReceiver)
        unbindService(serviceCallback)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(dataReceiver, dataFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(dataReceiver)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finishDialog.show()
            return false
        }
        return super.onKeyDown(keyCode, event)
    }

}