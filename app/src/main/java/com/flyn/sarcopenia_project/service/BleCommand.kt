package com.flyn.sarcopenia_project.service

enum class BleCommand {
    NOTIFICATION_ON, NOTIFICATION_OFF;

    companion object {

        private val actions = values().map { it.name }

        fun contentAction(name: String?): BleCommand? {
            if (name == null) return null
            return if (actions.contains(name)) valueOf(name)
            else null
        }
    }

}