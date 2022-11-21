package com.flyn.sarcopenia_project.file

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.flyn.sarcopenia_project.MainActivity
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.net.Client
import com.flyn.sarcopenia_project.utils.FileManager
import io.netty.channel.ConnectTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class FileItemAdapter: RecyclerView.Adapter<FileItemAdapter.ViewHolder>() {

    companion object {
        const val FILE_DATA = "FileData"
//        private const val HOST = "140.135.101.61"
        private const val HOST = "140.135.101.71"
    }

    inner class ViewHolder(val view: View): RecyclerView.ViewHolder(view) {

        // TODO add file path
        val selected: CheckBox = view.findViewById(R.id.file_select_box)
        val nameText: TextView = view.findViewById(R.id.file_name)
        val detailText: TextView = view.findViewById(R.id.file_detail)

        init {
            view.setOnClickListener {
                if (isSelected) selected.isChecked = !selected.isChecked
                else {
                    val intent = Intent(context, FileDataViewer::class.java)
                    intent.putExtra(FILE_DATA, fileList.elementAt(adapterPosition))
                    context.startActivity(intent)
                }
            }
            view.setOnLongClickListener {
                if (isSelected) return@setOnLongClickListener false
                selected.isChecked = true
                true
            }
            selected.setOnCheckedChangeListener { _, b ->
                selectedEvent(b)
            }
        }

        private fun selectedEvent(isSelected: Boolean) {
            if (isSelected) {
                selectedList.add(adapterPosition)
                view.setBackgroundColor(context.getColor(R.color.light_gray))
                if (selectedList.size == 1) isItemSelected(true)
            }
            else {
                selectedList.remove(adapterPosition)
                view.setBackgroundColor(context.getColor(R.color.white))
                if (selectedList.size == 0) isItemSelected(false)
            }
            selectedList.forEach(::print)
            println()
            selectedChange()
        }
    }

    private val fileList = mutableListOf<SensorFile>()
    private val selectedList = mutableSetOf<Int>()
    private val viewList = mutableSetOf<ViewHolder>()

    private lateinit var context: Context
    private var isSelected = false

    @SuppressLint("NotifyDataSetChanged")
    fun isItemSelected(enable: Boolean) {
        isSelected = enable
        if (enable) context.sendBroadcast(Intent(FileManagerAction.ITEM_SELECTED.name))
        else {
            selectedList.clear()
            context.sendBroadcast(Intent(FileManagerAction.ITEM_UNSELECTED.name))
        }
        GlobalScope.launch(Dispatchers.Main) {
            viewList.forEach {
                if (isSelected) it.selected.visibility = View.VISIBLE
                else {
                    it.selected.visibility = View.GONE
                    it.selected.isChecked = false
                    it.view.setBackgroundColor(context.getColor(R.color.white))
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun scanFiles() {
        fileList.clear()
        val dir = FileManager.RECORDING_DIR
        if (!dir.exists()) return
        dir.listFiles { file ->
            file.extension == "csv"
        }?.forEach {
            fileList.add(SensorFile(it))
        }
        fileList.sort()
        notifyDataSetChanged()
    }

    fun deleteFile() {
        val dir = FileManager.RECORDING_DIR
        fileList.filterIndexed { index, _ ->
            selectedList.contains(index)
        }.forEach { file ->
            File(dir, file.name).delete()
        }
        isItemSelected(false)
        scanFiles()
    }

    fun cloudSave() {
        val dir = FileManager.RECORDING_DIR
        val files = fileList.filterIndexed { index, _ ->
            selectedList.contains(index)
        }.map {
            File(dir, it.name)
        }.toTypedArray()
        files.forEach {
            println(it.absolutePath)
        }
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Client.transferFiles(context, files, HOST)
            } catch (exception: ConnectTimeoutException) {
                println("Time out")
            }

        }
        isItemSelected(false)
    }

    private fun selectedChange() {
        Intent(FileManagerAction.ITEM_AMOUNT.name).run {
            putExtra(FileManagerAction.ITEM_AMOUNT.name, selectedList.size)
            context.sendBroadcast(this)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_file_data, parent, false)
        context = parent.context
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        viewList.add(holder)
        val file = fileList.elementAt(position)
        with(holder) {
            nameText.text = file.name
            detailText.text = context.getString(R.string.file_detail, file.size)
        }
    }

    override fun getItemCount(): Int = fileList.size

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        viewList.remove(holder)
    }

}