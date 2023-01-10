package com.flyn.sarcopenia_project.viewer

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.utils.ExtraManager
import com.flyn.sarcopenia_project.utils.TimeManager
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
import kotlin.math.ceil

class DataPage(private val min: Float, private val max: Float,
               private vararg val names: String,
               private val yAxisFormatter: ((Float) -> String)? = null) : Fragment() {

    companion object {
        private val dataFormat = SimpleDateFormat("mm:ss.SSS", Locale("zh", "tw"))
        private val colorSet = setOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA)
    }

    private lateinit var leftChart: LineChart
    private lateinit var rightChart: LineChart
    private var hasInit = false
    private var prevTime = 0L

    fun addData(deviceIndex: Int, values: List<FloatArray>) {
        if (context == null) return
        if (requireActivity() !is DataViewer) return
        when (deviceIndex) {
            ExtraManager.LEFT_DEVICE -> addDataToChart(leftChart, values)
            ExtraManager.RIGHT_DEVICE -> addDataToChart(rightChart, values)
        }
//        val samplingRate: Double = dataAmount / time.toDouble() * 1000
//        samplingRateText.text = getString(R.string.sampling_rate, samplingRate)
    }

    private fun addDataToChart(chart: LineChart, list: List<FloatArray>) {
        var pos = TimeManager.time.toDouble().toFloat()
        val interval = (TimeManager.time - prevTime).toFloat() / list.size
        list.forEach { values ->
            values.forEachIndexed { index, data ->
                chart.data.run {
                    addEntry(Entry(ceil(pos), data), index)
                }
            }
            pos += interval
        }
        prevTime = TimeManager.time
        chart.data.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(5000f)
        chart.moveViewToX(pos  - 4000f)
    }

    private fun initChart(chart: LineChart) {
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

    private fun initDataSet(chart: LineChart) {
        var ptr = 0
        val dataSetList = mutableListOf<ILineDataSet>()
        names.forEach { name ->
            LineDataSet(null, name).apply {
                lineWidth = 1.5f
                color = colorSet.elementAtOrElse(ptr) { Color.BLACK }
                circleColors = mutableListOf(color)
                ptr += 1
                setDrawCircles(false)
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

        leftChart = view.findViewById(R.id.file_viewer_chart_left)
        initChart(leftChart)
        initDataSet(leftChart)

        rightChart = view.findViewById(R.id.file_viewer_chart_right)
        initChart(rightChart)
        initDataSet(rightChart)

        hasInit = true
        return view
    }

}