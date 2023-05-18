package com.flyn.sarcopenia_project.viewer

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.service.BluetoothLeService
import com.flyn.sarcopenia_project.service.ScanDeviceActivity
import com.flyn.sarcopenia_project.utils.ActionManager
import com.flyn.sarcopenia_project.utils.ExtraManager

class DeviceSelectActivity: AppCompatActivity() {
    object Foot{
        var foot = "male"
    }
    private lateinit var select_insole: Spinner
//    lateinit var foot : String
    private val startButton: Button by lazy { findViewById(R.id.start_sampling_button) }
    private val devices: Array<DeviceSelector> by lazy {
        arrayOf(
            findViewById(R.id.right_foot),
            findViewById(R.id.left_foot)
        )
    }
    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(componentName: ComponentName?, binder: IBinder?) {
            service = (binder as BluetoothLeService.BleServiceBinder).getService()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            service = null
        }

    }
    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent) {
            val name = intent.getStringExtra(ExtraManager.DEVICE_NAME)
            val address = intent.getStringExtra(ExtraManager.DEVICE_ADDRESS)
            val index = intent.getIntExtra(ExtraManager.DEVICE_INDEX, -1)
            if (name == null || address == null || index < 0) return
            when (intent.action) {
                ActionManager.GATT_CONNECTED -> {
                    devices[index].addDevice(name, address)
                }
                ActionManager.GATT_DISCONNECTED -> {
                    devices[index].removeDevice()
                }
            }
            startButton.isEnabled = run {
                var enable = true
                devices.forEach { selector ->
                    enable = selector.hasDevice && enable
                }
                enable
            }
        }

    }

    private var service: BluetoothLeService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_select)

        startService(Intent(this, BluetoothLeService::class.java))
        devices.forEachIndexed { index, device ->
            device.setOnClickListener {
                if (!device.hasDevice) {
                    Intent(this, ScanDeviceActivity::class.java).let {
                        it.putExtra(ExtraManager.DEVICE_INDEX, index)
                        startActivity(it)
                    }
                }
            }
            device.setDisconnectCallback {
                service?.disconnect(index)
            }
        }
        select_insole = findViewById(R.id.select_foot)
        val adapter = ArrayAdapter.createFromResource(this, R.array.spinner_items, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        select_insole.adapter = adapter
        select_insole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Foot.foot = position.toString()
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {

            }
        }
        startButton.setOnClickListener {
            startActivity(Intent(this, DataViewer::class.java))
            finish()
        }
        IntentFilter().run {
            addAction(ActionManager.GATT_CONNECTED)
            addAction(ActionManager.GATT_DISCONNECTED)
            registerReceiver(receiver, this)
        }
    }

    override fun onResume() {
        super.onResume()
        Intent(this, BluetoothLeService::class.java).let {
            bindService(it, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onPause() {
        super.onPause()
        unbindService(serviceConnection)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

}