package com.flyn.sarcopenia_project.service

enum class BleAction {

    GATT_CONNECTED, GATT_DISCONNECTED,
    EMG_LEFT_DATA_AVAILABLE, EMG_RIGHT_DATA_AVAILABLE,
    ACC_DATA_AVAILABLE, GYR_DATA_AVAILABLE;

}