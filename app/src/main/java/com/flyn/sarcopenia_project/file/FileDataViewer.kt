package com.flyn.sarcopenia_project.file

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.flyn.sarcopenia_project.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileDataViewer: AppCompatActivity() {

    companion object {
        private val dataFormat = SimpleDateFormat("mm:ss.SSS", Locale("zh", "tw"))
        private val colorSet = setOf(Color.RED, Color.GREEN, Color.BLUE)
        private val emgTagPattern = Regex("""EMG,(\d+)""")
        private val accTagPattern = Regex("""ACC,(\d+)""")
        private val gyrTagPattern = Regex("""GYR,(\d+)""")
        private val emgPattern = Regex("""(\d+),(\d+)""")
        private val imuPattern = Regex("""(\d+),([-]?\d+),([-]?\d+),([-]?\d+)""")
    }

    private val emgChart: LineChart by lazy { findViewById(R.id.file_viewer_emg_chart) }
    private val accChart: LineChart by lazy { findViewById(R.id.file_viewer_acc_chart) }
    private val gyrChart: LineChart by lazy { findViewById(R.id.file_viewer_gyr_chart) }

    private fun initChart(chart: LineChart, names: Array<String>, min: Float, max: Float, yAxisFormatter: (Float) -> String) {
        with(chart) {
//            isDragEnabled = false
//            setScaleEnabled(false)
            description.isEnabled = false
            /* x axis */
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                enableGridDashedLine(10f, 10f, 0f)
                valueFormatter = object: ValueFormatter() {

                    override fun getFormattedValue(value: Float): String {
                        return dataFormat.format((value * 1000).toLong())
                    }

                }
            }
            /* disable right axis */
            axisRight.isEnabled = false
            /* y axis */
            axisLeft.apply {
                enableGridDashedLine(10f, 10f, 0f)
                axisMaximum = max
                axisMinimum = min
                valueFormatter = object: ValueFormatter() {

                    override fun getFormattedValue(value: Float): String {
                        return yAxisFormatter.invoke(value)?:value.toString()
                    }

                }
            }
        }
        initChartSet(chart, names, yAxisFormatter)
    }

    private fun initChartSet(chart: LineChart, names: Array<String>, valueFormatter: (Float) -> String) {
        var ptr = 0
        val dataSetList = mutableListOf<ILineDataSet>()
        names.forEach { name ->
            LineDataSet(null, name).apply {
                lineWidth = 1.5f
                color = colorSet.elementAtOrElse(ptr) { Color.BLACK }
                circleColors = mutableListOf(color)
                ptr += 1

                axisDependency = YAxis.AxisDependency.LEFT
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawValues(false)
            }.let { dataSetList.add(it) }
        }
        chart.data = LineData(dataSetList)
        object: ValueFormatter() {

            override fun getFormattedValue(value: Float): String {
                return valueFormatter.invoke(value)
            }

        }.let { chart.data.setValueFormatter(it) }
        chart.invalidate()
    }

    private fun addData(chart: LineChart, values: Map<Long, Array<Short>>) {
        println("add data size: ${values.size}")
        with (chart.data) {
            values.forEach { (time, value) ->
                val pos = time.toFloat() / 1000f
                value.forEachIndexed { index, data ->
                    addEntry(Entry(pos, data.toFloat()), index)
                }
            }
        }
//        println(chart.visibleXRange)
//        chart.setVisibleXRangeMaximum(5f)
//        chart.moveViewToX(0f)
        chart.data.notifyDataChanged()
        chart.notifyDataSetChanged()
    }

    private suspend fun dataParser(file: SensorFile) {
        withContext(Dispatchers.IO) {
            val dir = File(filesDir, "record")
            File(dir, file.name).bufferedReader().use { reader ->
                with(reader) {
                    // parse emg data
                    println("start parse")
                    val emgDataAmount = getInt(readLine(), emgTagPattern)?: return@use
                    println("emg tag parse success")
                    val emgValues = mutableMapOf<Long, Array<Short>>()
                    for (i in 1..emgDataAmount) {
                        readLine()?.let {
                            emgPattern.find(it)!!.destructured.let { (s1, s2) ->
                                val time = s1.toLongOrNull()?: return@use
                                val value = s2.toShortOrNull()?: return@use
                                emgValues[time] = arrayOf(value)
                            }
                        }?: return@use
                    }
                    addData(emgChart, emgValues.toSortedMap())
                    // parse acc data
                    val accDataAmount = getInt(readLine(), accTagPattern)?: return@use
                    println("acc tag parse success")
                    val accValues = mutableMapOf<Long, Array<Short>>()
                    for (i in 1..accDataAmount) {
                        readLine()?.let {
                            imuPattern.find(it)!!.destructured.let { (s1, s2, s3, s4) ->
                                val time = s1.toLongOrNull()?: return@use
                                val x = s2.toShortOrNull()?: return@use
                                val y = s3.toShortOrNull()?: return@use
                                val z = s4.toShortOrNull()?: return@use
                                accValues[time] = arrayOf(x, y, z)
                            }
                        }?: return@use
                    }
                    addData(accChart, accValues.toSortedMap())
                    // parse gyr data
                    val gyrDataAmount = getInt(readLine(), gyrTagPattern)?: return@use
                    println("gyr tag parse success")
                    val gyrValues = mutableMapOf<Long, Array<Short>>()
                    for (i in 1..gyrDataAmount) {
                        readLine()?.let {
                            imuPattern.find(it)!!.destructured.let { (s1, s2, s3, s4) ->
                                val time = s1.toLongOrNull()?: return@use
                                val x = s2.toShortOrNull()?: return@use
                                val y = s3.toShortOrNull()?: return@use
                                val z = s4.toShortOrNull()?: return@use
                                gyrValues[time] = arrayOf(x, y, z)
                            }
                        }?: return@use
                    }
                    addData(gyrChart, gyrValues.toSortedMap())
                }
            }
        }
    }

    private fun getInt(text: String?, pattern: Regex): Int? {
        if (text == null) return null
        val (numText) = pattern.find(text)!!.destructured
        return numText.toIntOrNull()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_data_viewer)
        // init emg chart
        initChart(emgChart, arrayOf("emg"), 0f, 1024f) { "%.2f V".format(it / 1023f * 3.6f) }
        // init acc chart
        initChart(accChart, arrayOf("ax", "ay", "az"), -32768f, 32768f) { "%.2f g".format(it / 32767f * 2f) }
        // init gyr chart
        initChart(gyrChart, arrayOf("gx", "gy", "gz"), -32768f, 32768f) { "%.2f rad/s".format(it / 32767f * 250f) }

        GlobalScope.launch {
            val file = intent.getSerializableExtra(FileItemAdapter.FILE_DATA)?: return@launch
            dataParser(file as SensorFile)
        }
    }

}