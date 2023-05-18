package com.flyn.sarcopenia_project.viewer

import android.app.Service
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.flyn.sarcopenia_project.MainActivity
import com.flyn.sarcopenia_project.R
import com.flyn.sarcopenia_project.service.BluetoothLeService
import com.flyn.sarcopenia_project.utils.ActionManager
import com.flyn.sarcopenia_project.utils.ExtraManager
import com.flyn.sarcopenia_project.utils.calibrate
import com.github.psambit9791.jdsp.signal.peaks.FindPeak
import com.github.psambit9791.jdsp.signal.peaks.Peak
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.*
import org.w3c.dom.Text
import java.util.*


class DataViewer: AppCompatActivity() {

    companion object {
        private const val TAG = "Data Viewer"
        private val dataFilter = IntentFilter().apply {
            addAction(ActionManager.ADC_DATA_AVAILABLE)
        }
    }
    private var datalist_L: LinkedList<Double> = LinkedList()
    private var gaitlist: LinkedList<Double> = LinkedList()
    private var datalist_R: LinkedList<Double> = LinkedList()
    private lateinit var filteredPeaks1 : IntArray
    private lateinit var filteredPeaks_t : IntArray
    private lateinit var filterGait : IntArray
    private lateinit var filterGait_t : IntArray
    private lateinit var filteredPeaks1_r : IntArray
    private lateinit var filteredPeaks_t_r : IntArray
    private lateinit var height: DoubleArray
    private lateinit var height_r: DoubleArray
//    private lateinit var select_insole: Spinner
//    private lateinit  var foot : String
    private var cop_x:Float = 0.0f
    private var cop_y:Float = 0.0f
    val time: Long
        get()=Date().time - startTime
    private val startTime = Date().time
    private var time_start : Long = 0
    private var heightAverage: Double = 0.0
    private var heightAverage_r: Double = 0.0
//    lateinit var foot : String
    private val adc = DataPage(0f, 2500f, "HA","LT","M1","M5","Arch","MH") { "%.2f g".format(it) }
    private val copPage = CopPage()
    private val gaitPage = GaitPage()
    private var PressureList: LinkedList<Double> = LinkedList()
    private var PressureList_r: LinkedList<Double> = LinkedList()
    private var i = 0
    private var m = 0
    lateinit var vibrator: Vibrator
    private lateinit var left_cop_x: TextView
    private lateinit var left_cop_y: TextView
    private lateinit var right_cop_x: TextView
    private lateinit var right_cop_y: TextView
    lateinit var vibrationEffect: VibrationEffect
    var insole = DeviceSelectActivity.Foot.foot
    private var step_duration : MutableList<Double> = mutableListOf()
    private var left_x: Float = 0.0f
    private var left_y: Float = 0.0f
    private var calibrateArgument = arrayOf(
        // right foot calibrate
        mapOf(
            "weight" to floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            "bias" to floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        ),
        // left foot calibrate
        mapOf(
            "weight" to floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            "bias" to floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        )
    )
    private val calibrateArgument_m = arrayOf(
        // right foot calibrate
        mapOf(
            "weight" to floatArrayOf(0.0015f, 0.0016f, 0.0014f, 0.0015f, 0.0014f, 0.0016f),
            "bias" to floatArrayOf(-0.4037f, -0.5995f, -0.6422f, -0.6353f, -0.7749f, -0.0776f),
//            "direct" to floatArrayOf(250f,380f,460f,430f,570f,50f)
            "direct" to floatArrayOf(350f,500f,500f,500f,500f,100f)
        ),
        // left foot calibrate
        mapOf(
            "weight" to floatArrayOf(0.0014f, 0.0015f, 0.0016f, 0.0014f, 0.0014f, 0.0014f),
            "bias" to floatArrayOf(-0.1262f, -0.6176f, -0.1515f, -0.8115f, -0.4956f, -0.0776f),
//            "direct" to floatArrayOf(70f,400f,100f,580f,190f,30f)
            "direct" to floatArrayOf(100f,150f,150f,150f,150f,0f)
        )
    )
    private val calibrateArgument_f = arrayOf(
        // right foot calibrate
        mapOf(
            "weight" to floatArrayOf(0.0014f, 0.0008f, 0.0016f, 0.001f, 0.0011f, 0.0013f),
            "bias" to floatArrayOf(0.411f, 0.1106f, 0.6439f, 0.153f, -0.0914f, 0.4504f)
        ),
        // left foot calibrate
        mapOf(
            "weight" to floatArrayOf(0.0014f, 0.0008f, 0.0016f, 0.001f, 0.0011f, 0.0013f),
            "bias" to floatArrayOf(0.411f, 0.1106f, 0.6439f, 0.153f, -0.0914f, 0.4504f)
        )
    )

    private val pageAdapter = object: FragmentStateAdapter(supportFragmentManager, lifecycle) {

        val fragments = arrayOf(adc, copPage, gaitPage)

        override fun getItemCount(): Int = fragments.size

        override fun createFragment(position: Int): Fragment = fragments[position]

    }

    private val dataReceiver = object: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
//            select_insole = findViewById(R.id.select_foot)
//            val adapter = ArrayAdapter.createFromResource(this@DataViewer, R.array.spinner_items, android.R.layout.simple_spinner_item)
//            adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
//            select_insole.adapter = adapter
//            select_insole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
//                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
//                    foot = position.toString()
//                }
//
//                override fun onNothingSelected(p0: AdapterView<*>?) {
//
//                }
//            }

            var left_cop :FloatArray
            var right_cop : FloatArray
            val data = intent.getShortArrayExtra(ExtraManager.BLE_DATA)!!
            val index = intent.getIntExtra(ExtraManager.DEVICE_INDEX, -1)
            when(insole){
                "0" -> calibrateArgument = calibrateArgument_m
                "1" -> calibrateArgument = calibrateArgument_f
            }
            Log.i("insole", insole)

            when (intent.action) {
                ActionManager.ADC_DATA_AVAILABLE -> {
                    val weight = calibrateArgument[index]["weight"]!!
                    val bias = calibrateArgument[index]["bias"]!!
                    val direct = calibrateArgument[index]["direct"]!!
                    adc.addData(index, listOf(calibrate(data, weight = weight, bias = bias, direct = direct)))
                    when(index){
                        ExtraManager.LEFT_DEVICE -> Log.i("left", calibrate(data,weight = weight,bias = bias, direct = direct).toList().toString())
                        ExtraManager.RIGHT_DEVICE -> Log.i("right", calibrate(data,weight = weight,bias = bias, direct = direct).toList().toString())
                    }
//                    Log.i("left", calibrate(data,weight = weight,bias = bias).toList().toString())
                    when (index) {
                        ExtraManager.LEFT_DEVICE -> {datalist_L.add(calibrate(data,weight = weight, bias = bias, direct = direct)[0].toDouble())
                                gaitlist.add(calibrate(data,weight = weight, bias = bias, direct = direct)[5].toDouble())
                        }
                        ExtraManager.RIGHT_DEVICE -> datalist_R.add(calibrate(data,weight = weight, bias = bias, direct = direct)[0].toDouble())
                    }
                    when(index){
                        ExtraManager.LEFT_DEVICE -> {copCalculate(calibrate(data,weight = weight, bias = bias, direct = direct)[0],
                            calibrate(data,weight = weight, bias = bias, direct = direct)[1],
                            calibrate(data,weight = weight, bias = bias, direct = direct)[2],
                            calibrate(data,weight = weight, bias = bias, direct = direct)[3],
                            calibrate(data,weight = weight, bias = bias, direct = direct)[4],
                            calibrate(data,weight = weight, bias = bias, direct = direct)[5])
                            left_cop = floatArrayOf(cop_x,cop_y)
                            left_x = left_cop[0]
                            left_y = left_cop[1]
                            copPage.setCop_L(left_x, left_y)


                        }
                        ExtraManager.RIGHT_DEVICE -> {copCalculate(calibrate(data,weight = weight, bias = bias, direct = direct)[0],
                            calibrate(data,weight = weight, bias = bias, direct = direct)[1],
                            calibrate(data,weight = weight, bias = bias, direct = direct)[2],
                            calibrate(data,weight = weight, bias = bias, direct = direct)[3],
                            calibrate(data,weight = weight, bias = bias, direct = direct)[4],
                            calibrate(data,weight = weight, bias = bias, direct = direct)[5])
                            right_cop = floatArrayOf(cop_x,cop_y)
                            val right_x = right_cop[0]
                            val right_y = right_cop[1]

                            copPage.setCop_R(right_x, right_y)

                        }
                    }

//                    Log.i("dataaa", datalist_R.toString())
                    val fp = FindPeak(datalist_L.toDoubleArray())
                    val fp_gait = FindPeak(gaitlist.toDoubleArray())
                    val fp_r = FindPeak(datalist_R.toDoubleArray())
                    val out: Peak = fp.detectPeaks()
                    val out_r: Peak = fp_r.detectPeaks()
                    val out_gait = fp_gait.detectPeaks()
//                    var isFatigue = false
                    filteredPeaks1 = out.filterByHeight(500.0, 20000.0)
                    filteredPeaks_t = out.filterByPeakDistance(filteredPeaks1,50)
                    filterGait = out_gait.filterByHeight(500.0, 20000.0)
                    filterGait_t = out_gait.filterByPeakDistance(filterGait,50)
                    filteredPeaks1_r = out_r.filterByHeight(500.0, 20000.0)
                    filteredPeaks_t_r = out_r.filterByPeakDistance(filteredPeaks1_r,50)
                    height = out.findPeakHeights(filteredPeaks_t)
                    height_r = out_r.findPeakHeights(filteredPeaks_t_r)
                    if(filterGait_t.isNotEmpty()) {
                        var velocity = (10 / ((filterGait_t.toMutableList().last()
                            .toDouble() - filterGait_t.toMutableList().first().toDouble()) / 1000))
                        var average_peak_value = height.average()
                        for (i in 0 until filterGait_t.toMutableList().size - 1) {
                            step_duration.add(i,
                                filterGait_t.get(i + 1).toDouble() - filterGait_t.get(i)
                                    .toDouble()
                            )
                        }
//                step_duration.add(0,(filteredPeaks1.get(1).toDouble()-filteredPeaks1.get(0).toDouble()))
                        var step_duration_average = step_duration.average()
                        gaitPage.setVelocity(velocity.toFloat())
                        gaitPage.setStepDuration(step_duration_average.toFloat())
                        if(gaitlist.size>1000){
                            gaitlist.removeFirst()
                        }

                    }

//                    Log.i("left", datalist_L.size.toString())
//                    Log.i("right", datalist_R.size.toString())
                    if (datalist_L.size >= 1000){
//                        Log.i("height", height.toList().toString())
                        if (time - time_start >= 10000){  //10000、15000、200000 By experimental protocol testing App whether detecting fatigue or not
                            if(height.isNotEmpty()){
                                heightAverage = height.average()
                                PressureList.add(heightAverage)
                                Log.i("pressure list left", PressureList.toString())

                                if(PressureList.size >= 2){
                                    if(PressureList.get(PressureList.size-1)<PressureList.first && PressureList.get(PressureList.size-1)<PressureList.get(PressureList.size-2)){
                                        i++
                                        if(i>=3){
//                                            isFatigue = true
                                            Toast.makeText(this@DataViewer,"Left Foot Fatigue Detected",
                                                Toast.LENGTH_LONG).show()
                                            vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                                            vibrationEffect = VibrationEffect.createOneShot(1000, 150)
                                            vibrator.vibrate(vibrationEffect)
//                                                vibrator.vibrate(1000)
                                            i = 0
                                        }
                                    }
                                    else{
                                        i = 0
                                    }
                                }
                            }
                            time_start = time
                        }
                        datalist_L.removeFirst()
//                            datalist.clear()
                    }
                    if (datalist_R.size >= 1000){
//                        Log.i("height", height_r.toList().toString())
                        if (time - time_start >= 10000){  //10000、15000、200000 By experimental protocol testing App whether detecting fatigue or not
                            if(height_r.isNotEmpty()){
                                heightAverage_r = height_r.average()
                                PressureList_r.add(heightAverage_r)
                                Log.i("pressure list right", PressureList_r.toString())
                                if(PressureList_r.size >= 2){
                                    if(PressureList_r.get(PressureList_r.size-1)<PressureList_r.first && PressureList_r.get(PressureList_r.size-1)<PressureList_r.get(PressureList_r.size-2)){
                                        m++
                                        if(m>=3){
//                                            isFatigue = true
                                            Toast.makeText(this@DataViewer,"Right Foot Fatigue Detected",
                                                Toast.LENGTH_LONG).show()
                                            vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                                            vibrationEffect = VibrationEffect.createOneShot(1000, 150)
                                            vibrator.vibrate(vibrationEffect)
//                                                vibrator.vibrate(1000)
                                            m = 0
                                        }
                                    }
                                    else{
                                        i = 0
                                    }
                                }
                            }
                            time_start = time
                        }
                        datalist_R.removeFirst()
//                            datalist.clear()
                    }

                }
            }
        }


    }

    private val serviceCallback = object: ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as BluetoothLeService.BleServiceBinder).getService()
            service.enableNotification(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service.enableNotification(false)
        }

    }

    private val tabSelector: TabLayout by lazy { findViewById(R.id.data_viewer_tab) }
    private val pager: ViewPager2 by lazy { findViewById(R.id.data_viewer)}
    private val saveButton: Button by lazy { findViewById(R.id.data_viewer_save_button) }
    private val finishDialog by lazy {
        MaterialAlertDialogBuilder(this).apply {
            setMessage(R.string.check_saving)
            setPositiveButton(R.string.save) { _, _ ->
                service.saveFile()
                finishSampling()
            }
            setNegativeButton(R.string.cancel) { _, _ ->
                finishSampling()
            }
        }
    }

    private lateinit var service: BluetoothLeService

    private fun copCalculate(ha: Float,lt: Float,m1: Float, m5: Float, arch: Float, mh: Float){
        val pressureArray = arrayOf(ha,lt,m1,m5,arch,mh)
        val pos_x = arrayOf<Float>(1.9f,-1.9f,1.6f,-1.5f,-0.1f,0.1f)
        val pos_y = arrayOf<Float>(8.4f,7f,2.1f,1.1f,-2.3f,-7.9f)
        val totalPressure = (ha+lt+m1+m5+arch+mh)
        for(i in 0..5){
            cop_x = (pressureArray[i] * pos_x[i]).toFloat()
            cop_y = (pressureArray[i] * pos_y[i]).toFloat()
        }
        cop_x = cop_x / totalPressure
        cop_y = cop_y / totalPressure
    }


    private fun finishSampling() {
        service.disconnectAll()
        stopService(Intent(this@DataViewer, BluetoothLeService::class.java))
        startActivity(Intent(this@DataViewer, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_viewer)

        val tagName = listOf(
            getString(R.string.foot_pressure_tab),
            getString(R.string.cop_tab),
            getString(R.string.gait_tab)
        )

        pager.adapter = pageAdapter
        TabLayoutMediator(tabSelector, pager) { tab, position ->
            tab.text = tagName[position]
        }.attach()

        saveButton.setOnClickListener {
            finishDialog.show()
        }

    }

    override fun onResume() {
        super.onResume()
        registerReceiver(dataReceiver, dataFilter)
        bindService(
            Intent(this, BluetoothLeService::class.java), serviceCallback,
            BIND_AUTO_CREATE
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(dataReceiver)
        unbindService(serviceCallback)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finishDialog.show()
            return false
        }
        return super.onKeyDown(keyCode, event)
    }

}