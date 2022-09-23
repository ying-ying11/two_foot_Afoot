package com.flyn.sarcopenia_project.service

import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

class BluetoothLeService: Service(), CoroutineScope by MainScope() {

    companion object {
        const val DATA = "BLE_DATA"
        private const val TAG = "Bluetooth Le Service"
    }

    private val leScanCallback = object: ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (result.device?.name == deviceName) {
                Log.d(TAG, "Find the target device")
                bluetoothScan(false)
                connect(result.device!!.address)
            }
        }

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
                bluetoothScan(true)
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
                }
                gatt.getService(UUIDList.IMU.uuid)?.let { service ->
                    characteristicSet.add(service.getCharacteristic(UUIDList.IMU_ACC.uuid))
                    characteristicSet.add(service.getCharacteristic(UUIDList.IMU_GYR.uuid))
                }?: run {
                    Log.e(TAG, "IMU service not found!")
                    gatt.disconnect()
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

    private val commandReceiver = object: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = BleCommand.contentAction(intent.action)?: return
            when (action) {
                BleCommand.NOTIFICATION_ON -> {
                    Log.d(TAG, "Notification ON")
                    characteristicSet.forEach {
                        enableNotification(it, true)
                    }
                }
                BleCommand.NOTIFICATION_OFF -> {
                    Log.d(TAG, "Notification OFF")
                    characteristicSet.forEach {
                        enableNotification(it, false)
                    }
                }
            }
        }

    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var isScanning = false
    private var waitingNotification = false
    private var deviceName = "Sarcopenia Project"
    private var deviceAddress = ""
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristicSet = mutableSetOf<BluetoothGattCharacteristic>()

    private fun bluetoothScan(enable: Boolean) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner?: run {
            Log.w(TAG, "Bluetooth adapter can not initialize")
            return
        }
        if (enable) {
            isScanning = true
            scanner.startScan(leScanCallback)
        }
        else {
            isScanning = false
            scanner.stopScan(leScanCallback)
        }
    }

    private fun connect(address: String) {
        if (address == deviceAddress && bluetoothGatt != null) {
            bluetoothGatt!!.connect()
            Log.d(TAG, "BLE reconnect")
            return
        }
        val device = bluetoothAdapter?.getRemoteDevice(address)?: return
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        deviceAddress = address
        Log.d(TAG, "BLE connect")
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

    private fun enableNotification(characteristic: BluetoothGattCharacteristic, enable: Boolean) {
        Log.i(TAG, "Notification is $enable")
        GlobalScope.launch {
            if (bluetoothAdapter == null) return@launch
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

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        IntentFilter().run {
            BleCommand.values().forEach {
                addAction(it.name)
            }
            registerReceiver(commandReceiver, this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        bluetoothScan(true)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(commandReceiver)
        if (isScanning) bluetoothScan(false)
        if (bluetoothAdapter == null) return
        characteristicSet.forEach {
            enableNotification(it, false)
        }
        bluetoothGatt?.disconnect()
        Log.d(TAG, "BLE disconnect")
    }

}