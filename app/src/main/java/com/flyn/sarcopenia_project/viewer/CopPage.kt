package com.flyn.sarcopenia_project.viewer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.utils.ExtraManager

class CopPage: Fragment() {

    private var isInit = false
    private lateinit var leftCopText: TextView
    private lateinit var rightCopText: TextView

    fun setCop_L(x: Float, y: Float) {
        if (!isInit) return
         leftCopText.text = getString(R.string.cop, x, y)
    }
    fun setCop_R(x: Float, y: Float) {
        if (!isInit) return
        rightCopText.text = getString(R.string.cop, x, y)

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_cop_page, container, false)
        isInit = true
        leftCopText = view.findViewById(R.id.left_cop)
        rightCopText = view.findViewById(R.id.right_cop)

        return view
    }

}