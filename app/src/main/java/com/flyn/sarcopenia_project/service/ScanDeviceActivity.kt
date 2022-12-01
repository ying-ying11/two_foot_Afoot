package com.flyn.sarcopenia_project.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flyn.sarcopenia_project.MainActivity
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.utils.ExtraManager

class ScanDeviceActivity: AppCompatActivity() {

    companion object {
        private const val TAG = "Scan Activity"
        private const val SCAN_PERIOD = 10000
    }

    private val scanCallback = object: ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            result.device.name?.let {
                ScannedListAdapter.addDevice(result.device)
            }
        }

    }

    private val scanButton: Button by lazy { findViewById(R.id.scan_device_button) }
    private val deviceList: RecyclerView by lazy { findViewById(R.id.scanned_device_list) }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val serviceConnection = object: ServiceConnection {

        override fun onServiceConnected(componentName: ComponentName?, binder: IBinder?) {
            service = (binder as BluetoothLeService.BleServiceBinder).getService()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            service = null
        }

    }

    private var isScanning = false
    private var service: BluetoothLeService? = null

    fun scanButton(view : View) {
        scanDevice(!isScanning)
    }

    private fun scanDevice(enable: Boolean) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner?:run {
            Log.w(TAG, "Bluetooth adapter can not initialize")
            return
        }
        if (enable) {
            ScannedListAdapter.clearDevice()
            Handler(Looper.getMainLooper()).postDelayed({
                scanDevice(false)
            }, SCAN_PERIOD.toLong())
            scanner.startScan(scanCallback)
            scanButton.text = getString(R.string.stop_scan_button)
        }
        else {
            scanner.stopScan(scanCallback)
            scanButton.text = getString(R.string.rescan_button)
        }
        isScanning = enable
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_device)

        deviceList.layoutManager = LinearLayoutManager(this)
        deviceList.adapter = ScannedListAdapter
        ScannedListAdapter.clickItemCallback { address ->
            val index = intent.getIntExtra(ExtraManager.DEVICE_INDEX, -1)
            service?.connect(index, address)
            finish()
        }

        // check device has BLE feature
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_support, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
        }

        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).run {
            if (!adapter.isEnabled) {
                registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == RESULT_OK) {
                        Log.e("Activity result", "OK")
                        // There are no request codes
                    }
                }.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
        scanDevice(true)
    }

    override fun onResume() {
        super.onResume()
        Intent(this, BluetoothLeService::class.java).let {
            bindService(it ,serviceConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onPause() {
        super.onPause()
        unbindService(serviceConnection)
    }

    private object ScannedListAdapter: RecyclerView.Adapter<ScannedListAdapter.ScannedHolder>() {

        private val deviceList = arrayListOf<BluetoothDevice>()
        private var callback: ((String) -> Unit)? = null

        fun addDevice(device: BluetoothDevice) {
            deviceList.map { it.address }.let {
                if (it.contains(device.address)) return
            }
            deviceList.add(device)
            notifyItemChanged(deviceList.size - 1)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun clearDevice() {
            deviceList.clear()
            notifyDataSetChanged()
        }

        fun clickItemCallback(callback: (String) -> Unit) {
            this.callback = callback
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScannedHolder {
            return ScannedHolder(
                LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_device_info, parent, false),
                callback
            )
        }

        override fun onBindViewHolder(holder: ScannedHolder, position: Int) {
            with(holder) {
                deviceList[position].let { device ->
                    deviceNameText.text = device.name
                    deviceAddressText.text = device.address
                }
            }
        }

        override fun getItemCount(): Int = deviceList.size

        private class ScannedHolder(view: View, callback: ((String) -> Unit)? = null)
            : RecyclerView.ViewHolder(view) {

            val deviceNameText: TextView = view.findViewById(R.id.device_name)
            val deviceAddressText: TextView = view.findViewById(R.id.device_address)

            init {
                view.setOnClickListener {
                    if (callback != null) {
                        callback(deviceAddressText.text as String)
                    }
                }
            }

        }

    }

}