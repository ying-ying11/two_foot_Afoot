package com.flyn.sarcopenia_project.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import android.widget.Toast
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.file.cache_file.CacheFile
import com.flyn.sarcopenia_project.file.cache_file.EmgCacheFile
import com.flyn.sarcopenia_project.file.cache_file.ImuCacheFile
import com.flyn.sarcopenia_project.utils.*
import kotlinx.coroutines.*
import java.util.*
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
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                sendStateBroadcast(ActionManager.GATT_DISCONNECTED, gatt.device)
                Log.i(TAG, "Disconnected from GATT server.")
                // TODO reconnect
                devices[getIndex(gatt.device.address)] = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (!checkService(gatt)) {
                    Log.e(TAG, "service not found!")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            this@BluetoothLeService, "This device not match", Toast.LENGTH_SHORT
                        ).show()
                    }
                    gatt.disconnect()
                    return
                }
                sendStateBroadcast(ActionManager.GATT_CONNECTED, gatt.device)
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
            val deviceIndex = getIndex(gatt.device.address)
            devices[deviceIndex]?.characteristic?.set(characteristic, true)
            when (characteristic.uuid) {
                UUIDList.EMG -> {
                    val data = EmgDecoder.decode(characteristic.value)
                    val file = EmgCacheFile(TimeManager.time, data)
                    writeFile(deviceIndex, FileManager.EMG_FILE_NAME, file)
                    dataCount[0] += data.size
                    sendDataBroadcast(deviceIndex, ActionManager.EMG_DATA_AVAILABLE, data)
                }
                UUIDList.IMU_ACC -> {
                    val data = characteristic.value.toShortArray()
                    val file = ImuCacheFile(TimeManager.time, data[0], data[1], data[2])
                    writeFile(deviceIndex, FileManager.IMU_ACC_FILE_NAME, file)
                    dataCount[2]++
                    sendDataBroadcast(deviceIndex, ActionManager.ACC_DATA_AVAILABLE, data)
                }
                UUIDList.IMU_GYR -> {
                    val data = characteristic.value.toShortArray()
                    val file = ImuCacheFile(TimeManager.time, data[0], data[1], data[2])
                    writeFile(deviceIndex, FileManager.IMU_GYR_FILE_NAME, file)
                    dataCount[3]++
                    sendDataBroadcast(deviceIndex, ActionManager.GYR_DATA_AVAILABLE, data)
                }
            }
        }

    }

    private val binder = BleServiceBinder()
    private val devices = arrayOfNulls<DeviceInfo>(2)
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
    private val handler = object: Handler(Looper.getMainLooper()) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                1 -> enableNotification()
            }
        }

    }

    private var lock = ReentrantLock()
    private var condition = lock.newCondition()

    fun connect(deviceIndex: Int, address: String): Boolean {
        if (devices[deviceIndex] != null) {
            return if (devices[deviceIndex]!!.address == address) {
                Log.d(TAG, "BLE reconnect")
                devices[deviceIndex]!!.gatt.connect()
            } else {
                Log.e(TAG, "device not match!")
                false
            }
        }
        val device = bluetoothAdapter.getRemoteDevice(address)?: return false
        devices[deviceIndex] = DeviceInfo(address,
            device.connectGatt(this, false, gattCallback))
        Log.d(TAG, "BLE connect")
        return true
    }

    fun disconnect(deviceIndex: Int) {
        devices[deviceIndex]?.let {
            enableNotification(it, false)
            it.gatt.close()
            it.gatt.disconnect()
        }
    }

    fun disconnectAll() {
        for (i in devices.indices) {
            disconnect(i)
        }
    }

    fun enableNotification(enable: Boolean) {
        if (enable) {
            TimeManager.resetTime()
            startForeground((System.nanoTime() % 10000).toInt(), notification)
            handler.sendEmptyMessageDelayed(1, 1000)
        }
        else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            handler.removeCallbacksAndMessages(null)
        }
        devices.forEach { device ->
            if (device == null) return@forEach
            enableNotification(device, enable)
        }
    }

    fun saveFile() {
        GlobalScope.launch(Dispatchers.IO) {
            FileManager.writeRecordFile(2)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, R.string.sava_completed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getIndex(address: String): Int {
        for (i in devices.indices) {
            if (address == devices[i]?.address) return i
        }
        return -1
    }

    private fun sendStateBroadcast(action: String, device: BluetoothDevice) {
        Intent(action).let {
            it.putExtra(ExtraManager.DEVICE_NAME, device.name)
            it.putExtra(ExtraManager.DEVICE_ADDRESS, device.address)
            it.putExtra(ExtraManager.DEVICE_INDEX, getIndex(device.address))
            sendBroadcast(it)
        }
    }

    private fun sendDataBroadcast(index: Int?, action: String, data: ShortArray) {
        Intent(action).let {
            it.putExtra(ExtraManager.BLE_DATA, data)
            it.putExtra(ExtraManager.DEVICE_INDEX, index)
            sendBroadcast(it)
        }
    }

    private fun enableNotification(device: DeviceInfo, enable: Boolean) {
        Log.i(TAG, "Notification is $enable")
        GlobalScope.launch(Dispatchers.Default) {
            device.characteristic.forEach { (characteristic, _) ->
                lock.lock()
                characteristic.getDescriptor(UUIDList.CCC).run {
                    value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    device.gatt.writeDescriptor(this)
                }
                device.gatt.setCharacteristicNotification(characteristic, enable)
                condition.await()
                lock.unlock()
            }
        }
    }

    private fun enableNotification() {
        GlobalScope.launch(Dispatchers.Default) {
            devices.forEach { device ->
                if (device == null) return@forEach
                device.characteristic.filter { (_, isEnable) ->
                    !isEnable
                }.forEach { (characteristic, _) ->
                    lock.lock()
                    characteristic.getDescriptor(UUIDList.CCC).run {
                        value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        device.gatt.writeDescriptor(this)
                    }
                    device.gatt.setCharacteristicNotification(characteristic, true)
                    condition.await()
                    lock.unlock()
                    device.characteristic[characteristic] = false
                }
            }
        }
        handler.sendEmptyMessageDelayed(1, 1000)
    }

    private fun checkService(gatt: BluetoothGatt): Boolean {
        // check service
        var result = gatt.services.map { it.uuid }
            .containsAll(listOf(UUIDList.ADC, UUIDList.IMU))
        if (!result) return false
        // check emg characteristic
        result = gatt.getService(UUIDList.ADC).characteristics.map { it.uuid }
            .containsAll(listOf(UUIDList.EMG))
        if (!result) return false
        // check acc & gyr characteristic
        result = gatt.getService(UUIDList.IMU).characteristics.map { it.uuid }
            .containsAll(listOf(UUIDList.IMU_ACC, UUIDList.IMU_GYR))
        return result
    }

    private fun writeFile(index: Int, fileName: String, file: CacheFile) {
        GlobalScope.launch(Dispatchers.IO) {
            FileManager.appendRecordData(index, fileName, file)
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
        disconnectAll()
        FileManager.removeTempRecord()
    }

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    inner class BleServiceBinder: Binder() {

        fun getService(): BluetoothLeService {
            return this@BluetoothLeService
        }

    }

    private class DeviceInfo(val address: String, val gatt: BluetoothGatt) {
        val characteristic: MutableMap<BluetoothGattCharacteristic, Boolean> by lazy {
            mutableMapOf(
                gatt.getService(UUIDList.ADC).getCharacteristic(UUIDList.EMG) to false,
                gatt.getService(UUIDList.IMU).getCharacteristic(UUIDList.IMU_ACC) to false,
                gatt.getService(UUIDList.IMU).getCharacteristic(UUIDList.IMU_GYR) to false
            )
        }

        var isConnected = false

    }

}