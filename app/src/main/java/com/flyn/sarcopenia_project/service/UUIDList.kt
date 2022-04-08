package com.flyn.sarcopenia_project.service

import java.util.*

private fun uuidEncode(w32: Long, w1: Long, w2: Long, w3: Long, w48: Long): UUID {
    val msb = (w32 shl 32) or (w1 shl 16) or w2
    val lsb = (w3 shl 48) or w48
    return UUID(msb, lsb)
}

enum class UUIDList(val uuid: UUID) {
    CCC(uuidEncode(0x00002902,0x0000, 0x1000, 0x8000, 0x00805f9b34fb)),
    ADC(uuidEncode(0xD16F7A3D, 0x1897, 0x40EA, 0x9629, 0xBDF749AC5990)),
    ADC_RAW(uuidEncode(0xD16F7A3D, 0x1897, 0x40EA, 0x9629, 0xBDF749AC5991)),
    IMU(uuidEncode(0x58C4FFA1, 0x1548, 0x44D5, 0x9972, 0xF7C25BECB620)),
    IMU_ACC(uuidEncode(0x58C4FFA1, 0x1548, 0x44D5, 0x9972, 0xF7C25BECB621)),
    IMU_GYR(uuidEncode(0x58C4FFA1, 0x1548, 0x44D5, 0x9972, 0xF7C25BECB622)),
    IMU_ACC_TEXT(uuidEncode(0x58C4FFA1, 0x1548, 0x44D5, 0x9972, 0xF7C25BECB623)),
    IMU_GYR_TEXT(uuidEncode(0x58C4FFA1, 0x1548, 0x44D5, 0x9972, 0xF7C25BECB624));

    companion object {

        private val uuidList = UUIDList.values().map { it.uuid to it.name }.toMap()

        fun getTitle(uuid: UUID, default: String): String {
            return uuidList[uuid]?: default
        }

    }

}