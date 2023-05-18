package com.flyn.sarcopenia_project

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton

class ModeSelect : AppCompatActivity() {

    private val dataViewerButton: ImageButton by lazy{findViewById(R.id.muscle_button)}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_select)

        dataViewerButton.setOnClickListener {
            startActivity(Intent(this, connectBle::class.java))
        }
    }
}