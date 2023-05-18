package com.flyn.sarcopenia_project

import android.Manifest
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.flyn.sarcopenia_project.file.FileManagerActivity
import com.flyn.sarcopenia_project.utils.FileManager
import com.flyn.sarcopenia_project.viewer.DeviceSelectActivity

class connectBle : AppCompatActivity() {

    @TargetApi(Build.VERSION_CODES.M)
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                permissions -> permissions.forEach { (_, value) ->
            if (!value) {
                Intent().run {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.fromParts("package", packageName, null)
                    startActivity(this)
                }
            }
        }
        }

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
        val permissionList = arrayListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        // Android 12+ require bluetooth scan & bluetooth connect permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionList.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionList.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        requestMultiplePermissions.launch(permissionList.toTypedArray())

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect_ble)
        checkPermission()

        // TODO move out to function
        dataViewerButton.setOnClickListener {
            startActivity(Intent(this, DeviceSelectActivity::class.java))
        }

        fileManagerButton.setOnClickListener {
            startActivity(Intent(this, FileManagerActivity::class.java))
        }

        FileManager.APP_DIR = filesDir
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finishAffinity()
            return false
        }
        return super.onKeyDown(keyCode, event)
    }
}