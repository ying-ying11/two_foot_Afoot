package com.flyn.sarcopenia_project.viewer

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.flyn.sarcopenia_project.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DataPage(private val min: Float, private val max: Float,
               private vararg val names: String,
               private val yAxisFormatter: ((Float) -> String)? = null) : Fragment() {

    companion object {
        private val dataFormat = SimpleDateFormat("mm:ss.SSS", Locale("zh", "tw"))
        private val colorSet = setOf(Color.RED, Color.GREEN, Color.BLUE)
    }

    private lateinit var chart: LineChart
    private lateinit var samplingRateText: TextView
    private lateinit var describeText: TextView
    private var hasInit = false
    private var prevTime = 0L

    fun addData(describe: String, vararg values: Short) {
        if (context == null) return
        if (requireActivity() !is DataViewer) return
        val time = (requireActivity() as DataViewer).time
        if (time - prevTime > 100) {
            prevTime = time
            addDataToChart(time, values.toTypedArray())
            describeText.text = describe
        }
    }

    fun updateSamplingRate(dataAmount: Int) {
        if (context == null) return
        if (requireActivity() !is DataViewer) return
        val time = (requireActivity() as DataViewer).time
        val samplingRate: Double = dataAmount / time.toDouble() * 1000
        samplingRateText.text = getString(R.string.sampling_rate, samplingRate)
    }

    private fun addDataToChart(time: Long, value: Array<Short>) {
        var pos = 0f
        value.forEachIndexed { index, data ->
            chart.data.run {
                pos = (time.toDouble() / 1000).toFloat()
                addEntry(Entry(pos, data.toFloat()), index)
            }
        }
        chart.data.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(5f)
        chart.moveViewToX(pos  - 4f)
    }

    private fun initChart() {
        with(chart) {
            isDragEnabled = false
            setScaleEnabled(false)
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
                        return yAxisFormatter?.invoke(value)?:value.toString()
                    }

                }
            }
        }
    }

    private fun initDataSet() {
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
        chart.invalidate()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_data_page, container, false)
        chart = view.findViewById(R.id.file_viewer_emg_chart)
        samplingRateText = view.findViewById(R.id.sampling_rate)
        describeText = view.findViewById(R.id.data_descriptor)
        initChart()
        initDataSet()
        hasInit = true
        return view
    }

}