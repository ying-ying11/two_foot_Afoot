package com.flyn.sarcopenia_project.service

import java.util.*

enum class UUIDList(val uuid: UUID) {
    CCC(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")),
    ADC(UUID.fromString("D16F7A3D-1897-40EA-9629-BDF749AC5990")),
    EMG_LEFT(UUID.fromString("D16F7A3D-1897-40EA-9629-BDF749AC5991")),
    EMG_RIGHT(UUID.fromString("D16F7A3D-1897-40EA-9629-BDF749AC5992")),
    IMU(UUID.fromString("58C4FFA1-1548-44D5-9972-F7C25BECB620")),
    IMU_ACC(UUID.fromString("58C4FFA1-1548-44D5-9972-F7C25BECB621")),
    IMU_GYR(UUID.fromString("58C4FFA1-1548-44D5-9972-F7C25BECB622")),
    IMU_ACC_TEXT(UUID.fromString("58C4FFA1-1548-44D5-9972-F7C25BECB623")),
    IMU_GYR_TEXT(UUID.fromString("58C4FFA1-1548-44D5-9972-F7C25BECB624"));

    companion object {

        private val uuidList = values().map { it.uuid to it.name }.toMap()

        fun getTitle(uuid: UUID, default: String): String {
            return uuidList[uuid]?: default
        }

    }

}