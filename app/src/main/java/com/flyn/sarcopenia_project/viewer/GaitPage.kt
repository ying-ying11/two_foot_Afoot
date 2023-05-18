package com.flyn.sarcopenia_project.viewer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.flyn.sarcopenia_project.R

class GaitPage: Fragment() {


    private var isInit = false
    private lateinit var stepDurationText: TextView
    private lateinit var velocityText: TextView

    fun setStepDuration(value: Float) {
        if (!isInit) return
        stepDurationText.text = getString(R.string.step_duration, value)
    }

    fun setVelocity(value: Float) {
        if (!isInit) return
        velocityText.text = getString(R.string.velocity, value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_gait_page, container, false)

        isInit = true

        stepDurationText = view.findViewById(R.id.step_duration)
        velocityText = view.findViewById(R.id.velocity)

        return view
    }

}
