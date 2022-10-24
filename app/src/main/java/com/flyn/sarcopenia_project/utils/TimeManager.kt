package com.flyn.sarcopenia_project.utils

import java.util.*

object TimeManager {

    val time: Long
        get() {
            if (resetFlag) {
                resetFlag = false
                startTime = Date().time
            }
            return Date().time - startTime
        }

    private var resetFlag = true
    private var startTime = 0L

    fun resetTime() {
        resetFlag = true
    }

}