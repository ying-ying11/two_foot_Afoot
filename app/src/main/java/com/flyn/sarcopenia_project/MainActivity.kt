package com.flyn.sarcopenia_project

import android.Manifest
import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.flyn.sarcopenia_project.file.FileManagerActivity
import com.flyn.sarcopenia_project.service.BluetoothLeService
import com.flyn.sarcopenia_project.service.BleAction
import com.flyn.sarcopenia_project.viewer.DataViewer
import java.io.File

class MainActivity: AppCompatActivity() {

    companion object {
        internal lateinit var APP_DIR: File
        private const val REQUEST_ENABLE_BT = 2
    }

    enum class PermissionList(val permission: String, val code: Int) {
        FINE_LOCATION_PERMISSION(Manifest.permission.ACCESS_FINE_LOCATION, 2),
        @RequiresApi(Build.VERSION_CODES.S)
        BLUETOOTH_SCAN(Manifest.permission.BLUETOOTH_SCAN, 4),
        @RequiresApi(Build.VERSION_CODES.S)
        BLUETOOTH_CONNECT(Manifest.permission.BLUETOOTH_CONNECT, 8)
    }

    private val bluetoothConnectReceiver = object: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BleAction.GATT_CONNECTED.name -> {
                    dataViewerButton.isEnabled = true
                }
                BleAction.GATT_DISCONNECTED.name -> {
                    dataViewerButton.isEnabled = false
                }
            }
        }

    }

    @TargetApi(Build.VERSION_CODES.M)
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions : Map<String, Boolean> ->
            // Do something if some permissions granted or denied
            permissions.entries.forEach { entry ->
                if (!entry.value) {
                    Intent().run {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", packageName, null)
                        startActivity(this)
                    }
                }
            }
        }

    private val connectingButton: Button by lazy { findViewById(R.id.main_device_connecting) }
    private val dataViewerButton: Button by lazy { findViewById(R.id.main_data_viewer) }
    private val fileManagerButton: Button by lazy { findViewById(R.id.main_file_manager) }

    private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)

    private fun checkPermission() {
        // Check to see if the Bluetooth classic feature is available.
        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH) }?.also {
            Toast.makeText(this, "Not support Bluetooth", Toast.LENGTH_SHORT).show()
            finish()
        }
        // Check to see if the BLE feature is available.
        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
            Toast.makeText(this, "Not support BluetoothLE", Toast.LENGTH_SHORT).show()
            finish()
        }

        /* 確認是否已開啟取得手機位置功能以及權限 */
        val permissionList = arrayListOf(PermissionList.FINE_LOCATION_PERMISSION)
        // Android 12+ require bluetooth scan & bluetooth connect permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionList.add(PermissionList.BLUETOOTH_SCAN)
            permissionList.add(PermissionList.BLUETOOTH_CONNECT)
        }
        requestMultiplePermissions.launch(permissionList.map { it.permission }.toTypedArray())

        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).run {
            if (!adapter.isEnabled) {
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).also {
                    startActivityForResult(it, REQUEST_ENABLE_BT)
                }
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermission()

        startService(Intent(this, BluetoothLeService::class.java))

        IntentFilter().run {
            addAction(BleAction.GATT_CONNECTED.name)
            addAction(BleAction.GATT_DISCONNECTED.name)
            registerReceiver(bluetoothConnectReceiver, this)
        }

        dataViewerButton.setOnClickListener {
            startActivity(Intent(this, DataViewer::class.java))
        }

        fileManagerButton.setOnClickListener {
            startActivity(Intent(this, FileManagerActivity::class.java))
        }

        APP_DIR = filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, BluetoothLeService::class.java))
        unregisterReceiver(bluetoothConnectReceiver)
    }

}