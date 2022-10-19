package com.flyn.sarcopenia_project.service

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
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
            Log.d(TAG, "Attempting to start service discovery: ${bluetoothGatt?.discoverServices()}")
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestMtu(247)
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                broadcastUpdate(BleAction.GATT_DISCONNECTED)
                Log.i(TAG, "Disconnected from GATT server.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristicSet.clear()
                gatt.getService(UUIDList.ADC.uuid)?.let { service ->
                    characteristicSet.add(service.getCharacteristic(UUIDList.EMG_LEFT.uuid))
                    characteristicSet.add(service.getCharacteristic(UUIDList.EMG_RIGHT.uuid))
                }?: run {
                    Log.e(TAG, "ADC service not found!")
                    gatt.disconnect()
                    return
                }
                gatt.getService(UUIDList.IMU.uuid)?.let { service ->
                    characteristicSet.add(service.getCharacteristic(UUIDList.IMU_ACC.uuid))
                    characteristicSet.add(service.getCharacteristic(UUIDList.IMU_GYR.uuid))
                }?: run {
                    Log.e(TAG, "IMU service not found!")
                    gatt.disconnect()
                    return
                }
                Log.d(TAG, "uuid list size: ${characteristicSet.size}")
                broadcastUpdate(BleAction.GATT_CONNECTED)
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
                UUIDList.EMG_LEFT.uuid -> {
                    val data = EmgDecoder.decode(characteristic.value)
                    broadcastUpdate(BleAction.EMG_LEFT_DATA_AVAILABLE, data)
                }
                UUIDList.EMG_RIGHT.uuid -> {
                    val data = EmgDecoder.decode(characteristic.value)
                    broadcastUpdate(BleAction.EMG_RIGHT_DATA_AVAILABLE, data)
                }
                UUIDList.IMU_ACC.uuid -> {
                    val data = characteristic.value.toShortArray()
                    broadcastUpdate(BleAction.ACC_DATA_AVAILABLE, data)
                }
                UUIDList.IMU_GYR.uuid -> {
                    val data = characteristic.value.toShortArray()
                    broadcastUpdate(BleAction.GYR_DATA_AVAILABLE, data)
                }
            }
        }

    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var deviceAddress = ""
    private var lock = ReentrantLock()
    private var condition = lock.newCondition()
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristicSet = mutableSetOf<BluetoothGattCharacteristic>()

    fun connect(address: String): Boolean {
        if (address == deviceAddress && bluetoothGatt != null) {
            Log.d(TAG, "BLE reconnect")
            return bluetoothGatt!!.connect()
        }
        val device = bluetoothAdapter.getRemoteDevice(address)?: return false
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        deviceAddress = address
        Log.d(TAG, "BLE connect")
        return true
    }

    fun enableNotification(enable: Boolean) {
        Log.i(TAG, "Notification is $enable")
        GlobalScope.launch(Dispatchers.Default) {
            characteristicSet.forEach {  characteristic ->
                lock.lock()
                characteristic.getDescriptor(UUIDList.CCC.uuid).run {
                    value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    bluetoothGatt?.writeDescriptor(this)
                }
                bluetoothGatt?.setCharacteristicNotification(characteristic, enable)
                condition.await()
                lock.unlock()
            }
        }
    }

    private fun broadcastUpdate(action: BleAction, data: ShortArray? = null) {
        val intent = Intent(action.name)
        if (data != null) intent.putExtra(DATA, data)
        sendBroadcast(intent)
    }

    override fun onBind(p0: Intent?): IBinder {
        return BleServiceBinder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        enableNotification(false)
        bluetoothGatt?.disconnect()
        Log.d(TAG, "BLE disconnect")
        return super.onUnbind(intent)
    }

    inner class BleServiceBinder: Binder() {

        fun getService(): BluetoothLeService {
            return this@BluetoothLeService
        }

    }

}