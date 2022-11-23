package com.flyn.sarcopenia_project.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.flyn.sarcopenia_project.R
import java.util.*

class DeviceSelector: ConstraintLayout {

    private val titleText: TextView by lazy { findViewById(R.id.selector_title) }
    private val addDevice: ConstraintLayout by lazy { findViewById(R.id.selector_device) }
    private val nameText: TextView by lazy { findViewById(R.id.selector_name) }
    private val addressText: TextView by lazy { findViewById(R.id.selector_address) }
    private val hintText: TextView by lazy { findViewById(R.id.selector_hint) }

    private var hasDevice = false

    constructor(context: Context): super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int): super(context, attrs, defStyle) {
        init(context, attrs)
    }

    @SuppressLint("Recycle")
    private fun init(context: Context, attrs: AttributeSet?) {
        View.inflate(context, R.layout.component_device_selector, this)
        if (attrs != null) {
            val attr = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.text))
            titleText.text = attr.getString(0)
        }
        addDevice.setOnClickListener {
            if (!hasDevice) {
                // TODO add device
                addDevice(UUID.randomUUID().toString(), UUID.randomUUID().toString())
            }
            else {
                removeDevice()
            }
        }
    }

    fun addDevice(name: String, address: String) {
        hasDevice = true
        nameText.text = name
        nameText.visibility = View.VISIBLE
        addressText.text = address
        addressText.visibility = View.VISIBLE
        hintText.visibility = View.GONE
    }

    fun removeDevice() {
        hasDevice = false
        nameText.visibility = View.GONE
        addressText.visibility = View.GONE
        hintText.visibility = View.VISIBLE
    }

}