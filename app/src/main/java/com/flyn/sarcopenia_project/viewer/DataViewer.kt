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
import com.flyn.sarcopenia_project.utils.calibrate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.*
import java.util.*


class DataViewer: AppCompatActivity() {

    companion object {
        private const val TAG = "Data Viewer"
        private val tagName = listOf("Foot Pressure")
        private val dataFilter = IntentFilter().apply {
            addAction(ActionManager.ADC_DATA_AVAILABLE)
        }
    }

    private val adc = DataPage(0f, 2500f, "HA","LT","M1","M5","Arch","HM") { "%.2f g".format(it) }

    private val pageAdapter = object: FragmentStateAdapter(supportFragmentManager, lifecycle) {

        val fragments = arrayOf(adc)

        override fun getItemCount(): Int = fragments.size

        override fun createFragment(position: Int): Fragment = fragments[position]

    }

    private val dataReceiver = object: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val data = intent.getShortArrayExtra(ExtraManager.BLE_DATA)!!
            val index = intent.getIntExtra(ExtraManager.DEVICE_INDEX, -1)
            when (intent.action) {
                ActionManager.ADC_DATA_AVAILABLE -> {
                    adc.addData(index, listOf(calibrate(data)))
                }
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
                service.saveFile()
                finishSampling()
            }
            setNegativeButton(R.string.cancel) { _, _ ->
                finishSampling()
            }
        }
    }

    private lateinit var service: BluetoothLeService

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