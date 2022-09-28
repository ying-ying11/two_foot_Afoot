package com.flyn.sarcopenia_project.file

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.helper.widget.Layer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flyn.sarcopenia_project.R

class FileManagerActivity: AppCompatActivity() {

    private val fileItemReceiver = object: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                FileManagerAction.ITEM_SELECTED.name -> {
                    selectedOptionBar.visibility = View.VISIBLE
                }
                FileManagerAction.ITEM_UNSELECTED.name -> {
                    selectedOptionBar.visibility = View.GONE
                }
                FileManagerAction.ITEM_AMOUNT.name -> {
                    val itemAmount = intent.getIntExtra(FileManagerAction.ITEM_AMOUNT.name, 0)
                    selectedAmountText.text = itemAmount.toString()
                }
            }
        }

    }

    private val cloudSaveButton: Button by lazy { findViewById(R.id.file_manager_cloud_save) }
    private val deleteButton: Button by lazy { findViewById(R.id.file_manager_delete) }
    private val cancelButton: Button by lazy { findViewById(R.id.file_manager_cancel_select_button) }
    private val selectedOptionBar: Layer by lazy { findViewById(R.id.layer) }
    private val selectedAmountText: TextView by lazy { findViewById(R.id.file_manager_file_selected_amount) }
    private val fileList: RecyclerView by lazy { findViewById(R.id.file_manager_file_list) }
    private val fileItemAdapter = FileItemAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_file_manager)
        fileList.layoutManager = LinearLayoutManager(this)
        fileList.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        fileList.adapter = fileItemAdapter

        IntentFilter().run {
            addAction(FileManagerAction.ITEM_SELECTED.name)
            addAction(FileManagerAction.ITEM_UNSELECTED.name)
            addAction(FileManagerAction.ITEM_AMOUNT.name)
            registerReceiver(fileItemReceiver, this)
        }

        // TODO move out to function
        cancelButton.setOnClickListener {
            fileItemAdapter.isItemSelected(false)
        }
        // TODO move out to function
        deleteButton.setOnClickListener {
            fileItemAdapter.deleteFile()
        }
        // TODO move out to function
        cloudSaveButton.setOnClickListener {
            fileItemAdapter.cloudSave()
        }

        fileItemAdapter.scanFiles()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(fileItemReceiver)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && selectedOptionBar.visibility == View.VISIBLE) {
            fileItemAdapter.isItemSelected(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

}