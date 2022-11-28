package com.flyn.sarcopenia_project.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.file.cache_file.CacheFile
import com.flyn.sarcopenia_project.file.cache_file.EmgCacheFile
import com.flyn.sarcopenia_project.file.cache_file.ImuCacheFile
import com.flyn.sarcopenia_project.utils.*
import com.flyn.sarcopenia_project.viewer.FileRecordService
import kotlinx.coroutines.*
import java.util.concurrent.locks.ReentrantLock

class BluetoothLeService: Service(), CoroutineScope by MainScope() {

    companion object {
        private const val TAG = "Bluetooth Le Service"

        private var isChannelInit = false
    }

    private val gattCallback = object: BluetoothGattCallback() {

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d(TAG, "BLE mtu change: $mtu")
            Log.d(TAG, "Connected to GATT server.")
            Log.d(TAG, "Attempting to start service discovery: ${gatt?.discoverServices()}")
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestMtu(247)
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Intent(ActionManager.GATT_DISCONNECTED).let {
                    it.putExtra(ExtraManager.DEVICE_NAME, gatt.device.name)
                    it.putExtra(ExtraManager.DEVICE_ADDRESS, gatt.device.address)
                    sendBroadcast(it)
                }
                Log.i(TAG, "Disconnected from GATT server.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (!checkService(gatt)) {
                    Log.e(TAG, "service not found!")
                    gatt.disconnect()
                    return
                }
                Intent(ActionManager.GATT_CONNECTED).let {
                    it.putExtra(ExtraManager.DEVICE_NAME, gatt.device.name)
                    it.putExtra(ExtraManager.DEVICE_ADDRESS, gatt.device.address)
                    sendBroadcast(it)
                }
            }
            else Log.w(TAG, "onServicesDiscovered received: $status")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            lock.lock()
            condition.signal()
            lock.unlock()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // TODO change file name with device name
            when (characteristic.uuid) {
                UUIDList.EMG_LEFT -> {
                    val data = EmgDecoder.decode(characteristic.value)
                    val file = EmgCacheFile(TimeManager.time, data)
                    writeFile(FileManager.EMG_LEFT_FILE_NAME, file)
                    dataCount[0] += data.size
                    Intent(ActionManager.EMG_LEFT_DATA_AVAILABLE).let {
                        it.putExtra(ExtraManager.BLE_DATA, data)
                        sendBroadcast(it)
                    }
                }
                UUIDList.EMG_RIGHT -> {
                    val data = EmgDecoder.decode(characteristic.value)
                    val file = EmgCacheFile(TimeManager.time, data)
                    writeFile(FileManager.EMG_RIGHT_FILE_NAME, file)
                    dataCount[1] += data.size
                    Intent(ActionManager.EMG_RIGHT_DATA_AVAILABLE).let {
                        it.putExtra(ExtraManager.BLE_DATA, data)
                        sendBroadcast(it)
                    }
                }
                UUIDList.IMU_ACC -> {
                    val data = characteristic.value.toShortArray()
                    val file = ImuCacheFile(TimeManager.time, data[0], data[1], data[2])
                    writeFile(FileManager.IMU_ACC_FILE_NAME, file)
                    dataCount[2]++
                    Intent(ActionManager.ACC_DATA_AVAILABLE).let {
                        it.putExtra(ExtraManager.BLE_DATA, data)
                        sendBroadcast(it)
                    }
                }
                UUIDList.IMU_GYR -> {
                    val data = characteristic.value.toShortArray()
                    val file = ImuCacheFile(TimeManager.time, data[0], data[1], data[2])
                    writeFile(FileManager.IMU_GYR_FILE_NAME, file)
                    dataCount[3]++
                    Intent(ActionManager.GYR_DATA_AVAILABLE).let {
                        it.putExtra(ExtraManager.BLE_DATA, data)
                        sendBroadcast(it)
                    }
                }
            }
        }

    }

    private val binder = BleServiceBinder()
    private val devices = mutableMapOf<String, DeviceInfo>()
    private val dataCount = mutableListOf(0, 0, 0, 0)
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val notification: Notification by lazy {
        Notification.Builder(this, "Sarcopenia project")
            .setContentTitle("Sarcopenia project")
            .setContentText("Data receiving")
            .build()
    }

    private var lock = ReentrantLock()
    private var condition = lock.newCondition()

    fun connect(address: String): Boolean {
        if (devices.containsKey(address)) {
            Log.d(TAG, "BLE reconnect")
            return devices[address]!!.gatt.connect()
        }
        val device = bluetoothAdapter.getRemoteDevice(address)?: return false
        devices[address] = DeviceInfo(device.connectGatt(this, false, gattCallback))
        Log.d(TAG, "BLE connect")
        return true
    }

    fun disconnect(address: String) {
        devices[address]?.gatt?.let {
            enableNotification(it, false)
            it.disconnect()
        }
        devices.remove(address)
    }

    fun enableNotification(enable: Boolean) {
        if (enable) {
            TimeManager.resetTime()
            startForeground(FileRecordService.NOTIFICATION_ID, notification)
        }
        else stopForeground(STOP_FOREGROUND_REMOVE)
        devices.forEach { (_, device) ->
            enableNotification(device.gatt, enable)
        }
    }

    private fun enableNotification(gatt: BluetoothGatt, enable: Boolean) {
        Log.i(TAG, "Notification is $enable")
        GlobalScope.launch(Dispatchers.Default) {
            val characteristicSet = mutableSetOf(
                gatt.getService(UUIDList.ADC).getCharacteristic(UUIDList.EMG_LEFT),
                gatt.getService(UUIDList.ADC).getCharacteristic(UUIDList.EMG_RIGHT),
                gatt.getService(UUIDList.IMU).getCharacteristic(UUIDList.IMU_ACC),
                gatt.getService(UUIDList.IMU).getCharacteristic(UUIDList.IMU_GYR),
            )
            characteristicSet.forEach {  characteristic ->
                lock.lock()
                characteristic.getDescriptor(UUIDList.CCC).run {
                    value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(this)
                }
                gatt.setCharacteristicNotification(characteristic, enable)
                condition.await()
                lock.unlock()
            }
        }
    }

    private fun checkService(gatt: BluetoothGatt): Boolean {
        var result = gatt.services.map { it.uuid }
            .containsAll(listOf(UUIDList.ADC, UUIDList.IMU))
        if (!result) return false
        result = gatt.getService(UUIDList.ADC).characteristics.map { it.uuid }
            .containsAll(listOf(UUIDList.EMG_LEFT, UUIDList.EMG_RIGHT))
        if (!result) return false
        result = gatt.getService(UUIDList.IMU).characteristics.map { it.uuid }
            .containsAll(listOf(UUIDList.IMU_ACC, UUIDList.IMU_GYR))
        return result
    }

    private fun disconnect() {
        devices.forEach { (_, device) ->
            device.gatt.let {
                enableNotification(it, false)
                it.disconnect()
            }
        }
        devices.clear()
    }

    private fun writeFile(fileName: String, file: CacheFile) {
        GlobalScope.launch(Dispatchers.IO) {
            FileManager.appendRecordData(fileName, file)
        }
    }

    private fun saveFile() {
        GlobalScope.launch(Dispatchers.IO) {
            FileManager.writeRecordFile(dataCount[0], dataCount[1], dataCount[2], dataCount[3])
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, R.string.sava_completed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate() {
        if (!isChannelInit) {
            val name: CharSequence = "Sarcopenia project"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("Sarcopenia project", name, importance)
            channel.description = "Sarcopenia project description"

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    inner class BleServiceBinder: Binder() {

        fun getService(): BluetoothLeService {
            return this@BluetoothLeService
        }

    }

    private class DeviceInfo(val gatt: BluetoothGatt) {
        var isConnected = false
    }

}