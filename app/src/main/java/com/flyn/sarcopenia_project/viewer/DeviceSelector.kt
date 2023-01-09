package com.flyn.sarcopenia_project.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
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
    private val disconnectButton: Button by lazy {findViewById(R.id.selector_disconnect_button)}

    var address = ""
        private set
    var hasDevice = false
        private set

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
    }

    fun addDevice(name: String, address: String) {
        hasDevice = true
        this.address = address
        nameText.text = name
        nameText.visibility = View.VISIBLE
        addressText.text = address
        addressText.visibility = View.VISIBLE
        disconnectButton.visibility = View.VISIBLE
        hintText.visibility = View.GONE
    }

    fun removeDevice() {
        hasDevice = false
        this.address = ""
        nameText.visibility = View.GONE
        addressText.visibility = View.GONE
        disconnectButton.visibility = View.GONE
        hintText.visibility = View.VISIBLE
    }

    fun setDisconnectCallback(callback: () -> Unit) {
        disconnectButton.setOnClickListener {
            callback()
            removeDevice()
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        addDevice.setOnClickListener(l)
    }

}