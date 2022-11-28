package com.flyn.sarcopenia_project.viewer

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.service.BluetoothLeService
import com.flyn.sarcopenia_project.service.ScanDeviceActivity
import com.flyn.sarcopenia_project.utils.ActionManager
import com.flyn.sarcopenia_project.utils.ExtraManager

class DeviceSelectActivity: AppCompatActivity() {

    private val startButton: Button by lazy { findViewById(R.id.start_sampling_button) }
    private val leftDevice: DeviceSelector by lazy { findViewById(R.id.left_foot) }
    private val rightDevice: DeviceSelector by lazy { findViewById(R.id.right_foot) }
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
            when (intent.action) {
                ActionManager.GATT_CONNECTED -> {
                    if (name == null || address == null) return
                    if (isRightDevice) rightDevice.addDevice(name, address)
                    else leftDevice.addDevice(name, address)
                }
                ActionManager.GATT_DISCONNECTED -> {
                    if (name == null || address == null) return
                    if (address == leftDevice.address) leftDevice.removeDevice()
                    if (address == rightDevice.address) rightDevice.removeDevice()
                }
            }
            startButton.isEnabled = leftDevice.hasDevice && rightDevice.hasDevice
        }

    }

    private var service: BluetoothLeService? = null
    private var isRightDevice = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_select)

        startService(Intent(this, BluetoothLeService::class.java))
        leftDevice.setOnClickListener {
            if (!leftDevice.hasDevice) {
                isRightDevice = false
                startActivity(Intent(this, ScanDeviceActivity::class.java))
            }
        }
        leftDevice.setDisconnectCallback {
            service?.disconnect(leftDevice.address)
        }
        rightDevice.setOnClickListener {
            if (!rightDevice.hasDevice) {
                isRightDevice = true
                startActivity(Intent(this, ScanDeviceActivity::class.java))
            }
        }
        rightDevice.setDisconnectCallback {
            service?.disconnect(rightDevice.address)
        }
    }

    override fun onResume() {
        super.onResume()
        Intent(this, BluetoothLeService::class.java).let {
            bindService(it, serviceConnection, BIND_AUTO_CREATE)
        }
        IntentFilter().run {
            addAction(ActionManager.GATT_CONNECTED)
            addAction(ActionManager.GATT_DISCONNECTED)
            registerReceiver(receiver, this)
        }
    }

    override fun onPause() {
        super.onPause()
        unbindService(serviceConnection)
        unregisterReceiver(receiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, BluetoothLeService::class.java))
    }

}