package com.flyn.sarcopenia_project.service

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.flyn.sarcopenia_project.utils.ActionManager
import com.flyn.sarcopenia_project.utils.toShortArray
import kotlinx.coroutines.*
import java.util.concurrent.locks.ReentrantLock

class BluetoothLeService: Service(), CoroutineScope by MainScope() {

    companion object {
        const val DATA = "BLE_DATA"
        private const val TAG = "Bluetooth Le Service"
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
                broadcastUpdate(ActionManager.GATT_DISCONNECTED)
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
                broadcastUpdate(ActionManager.GATT_CONNECTED)
            }
            else Log.w(TAG, "onServicesDiscovered received: $status")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            lock.lock()
            condition.signal()
            lock.unlock()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid) {
                UUIDList.EMG_LEFT -> {
                    val data = EmgDecoder.decode(characteristic.value)
                    broadcastUpdate(ActionManager.EMG_LEFT_DATA_AVAILABLE, data)
                }
                UUIDList.EMG_RIGHT -> {
                    val data = EmgDecoder.decode(characteristic.value)
                    broadcastUpdate(ActionManager.EMG_RIGHT_DATA_AVAILABLE, data)
                }
                UUIDList.IMU_ACC -> {
                    val data = characteristic.value.toShortArray()
                    broadcastUpdate(ActionManager.ACC_DATA_AVAILABLE, data)
                }
                UUIDList.IMU_GYR -> {
                    val data = characteristic.value.toShortArray()
                    broadcastUpdate(ActionManager.GYR_DATA_AVAILABLE, data)
                }
            }
        }

    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val devices = mutableMapOf<String, DeviceInfo>()

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

    fun enableNotification(enable: Boolean) {
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

    private fun broadcastUpdate(action: String, data: ShortArray? = null) {
        val intent = Intent(action)
        if (data != null) intent.putExtra(DATA, data)
        sendBroadcast(intent)
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

    override fun onBind(p0: Intent?): IBinder {
        return BleServiceBinder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        Log.d(TAG, "BLE disconnect")
        return super.onUnbind(intent)
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