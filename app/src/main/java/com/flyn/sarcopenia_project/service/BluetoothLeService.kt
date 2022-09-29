package com.flyn.sarcopenia_project.service

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

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
            waitingNotification = false
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid) {
                UUIDList.EMG_LEFT.uuid -> broadcastUpdate(BleAction.EMG_LEFT_DATA_AVAILABLE, characteristic)
                UUIDList.EMG_RIGHT.uuid -> broadcastUpdate(BleAction.EMG_RIGHT_DATA_AVAILABLE, characteristic)
                UUIDList.IMU_ACC.uuid -> broadcastUpdate(BleAction.ACC_DATA_AVAILABLE, characteristic)
                UUIDList.IMU_GYR.uuid -> broadcastUpdate(BleAction.GYR_DATA_AVAILABLE, characteristic)
            }
        }

    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var waitingNotification = false
    private var deviceAddress = ""
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
        GlobalScope.launch {
            characteristicSet.forEach {  characteristic ->
                // TODO use ReentrantLock condition instead
                while (waitingNotification) {
                    delay(1)
                }
                waitingNotification = true
                characteristic.getDescriptor(UUIDList.CCC.uuid).run {
                    value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    bluetoothGatt?.writeDescriptor(this)
                }
                bluetoothGatt?.setCharacteristicNotification(characteristic, enable)
            }
        }
    }

    private fun broadcastUpdate(action: BleAction, characteristic: BluetoothGattCharacteristic? = null) {
        val intent = Intent(action.name)
        if (characteristic != null) {
            val data = characteristic.value
            if (data != null && data.isNotEmpty()) {
                intent.putExtra(DATA, data)
            }
        }
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