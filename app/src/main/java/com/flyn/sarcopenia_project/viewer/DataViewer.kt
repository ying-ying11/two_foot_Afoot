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
            addAction(ActionManager.EMG_DATA_AVAILABLE)
            addAction(ActionManager.ACC_DATA_AVAILABLE)
            addAction(ActionManager.GYR_DATA_AVAILABLE)
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

    private val dataReceiver = object: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val data = intent.getShortArrayExtra(ExtraManager.BLE_DATA)!!
            val index = intent.getIntExtra(ExtraManager.DEVICE_INDEX, -1)
            when (intent.action) {
                ActionManager.EMG_DATA_AVAILABLE -> emg.addData(index, data[0])
                ActionManager.ACC_DATA_AVAILABLE -> acc.addData(index, data[0], data[1], data[2])
                ActionManager.GYR_DATA_AVAILABLE -> gyr.addData(index, data[0], data[1], data[2])
            }
        }

    }

    private val serviceCallback = object: ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as BluetoothLeService.BleServiceBinder).getService()
            service.enableNotification(true)
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
                // TODO
//                service.saveFile()
                finishSampling()
            }
            setNegativeButton(R.string.cancel) { _, _ ->
                finishSampling()
            }
        }
    }

    private lateinit var service: BluetoothLeService

    private fun emgTransform(value: Short): Float = value.toFloat() / 4095f * 3.6f
    private fun emgTransform(value: Float): Float = value / 4095f * 3.6f

    private fun accTransform(value: Short): Float = value.toFloat() / 32767f * 2f
    private fun accTransform(value: Float): Float = value / 32767f * 2f

    private fun gyrTransform(value: Short): Float = value.toFloat() / 32767f * 250f
    private fun gyrTransform(value: Float): Float = value / 32767f * 250f

    private fun finishSampling() {
        service.disconnectAll()
        stopService(Intent(this@DataViewer, BluetoothLeService::class.java))
        startActivity(Intent(this@DataViewer, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_viewer)

        pager.adapter = pageAdapter
        TabLayoutMediator(tabSelector, pager) { tab, position ->
            tab.text = tagName[position]
        }.attach()

        saveButton.setOnClickListener {
            finishDialog.show()
        }

    }

    override fun onResume() {
        super.onResume()
        registerReceiver(dataReceiver, dataFilter)
        bindService(
            Intent(this, BluetoothLeService::class.java), serviceCallback,
            BIND_AUTO_CREATE
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(dataReceiver)
        unbindService(serviceCallback)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finishDialog.show()
            return false
        }
        return super.onKeyDown(keyCode, event)
    }

}