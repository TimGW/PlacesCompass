package com.timgortworst.placescompass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class Compass(activity: AppCompatActivity) : SensorEventListener {

    interface CompassListener {
        fun onNewBearing(bearing: Bearing)
    }

    private var start: LatLng? = null
    private var end: LatLng? = null
    private var listener: CompassListener? = null
    private val sensorManager: SensorManager = activity
        .getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gsensor: Sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val msensor: Sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val mGravity = FloatArray(3)
    private val mGeomagnetic = FloatArray(3)
    private val R = FloatArray(9)
    private val I = FloatArray(9)

    fun start() {
        sensorManager.registerListener(this, gsensor, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, msensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun setListener(l: CompassListener?) {
        listener = l
    }

    override fun onSensorChanged(event: SensorEvent) {
        val alpha = 0.97f
        synchronized(this) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                mGravity[0] = alpha * mGravity[0] + (1 - alpha) * event.values[0]
                mGravity[1] = alpha * mGravity[1] + (1 - alpha) * event.values[1]
                mGravity[2] = alpha * mGravity[2] + (1 - alpha) * event.values[2]
            }
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha) * event.values[0]
                mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha) * event.values[1]
                mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha) * event.values[2]
            }
            val success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic)
            if (success) {
                val orientation = FloatArray(3); SensorManager.getOrientation(R, orientation)
                val azimuth: Double = Math.toDegrees(orientation[0].toDouble()) // orientation

                if (start != null && end != null) {
                    val results = FloatArray(3)
                    Location.distanceBetween(
                        start!!.latitude, start!!.longitude,
                        end!!.latitude, end!!.longitude,
                        results
                    )
                    val bearing = getBearing(azimuth - results.last().toDouble())
                    val north = getBearing(azimuth)
                    listener?.onNewBearing(Bearing(bearing, north, results.first()))
                } else {
                    val north = getBearing(azimuth)
                    listener?.onNewBearing(Bearing(north, north, null))
                }
            }
        }
    }

    private fun getBearing(azimuth: Double) = ((azimuth + 360) % 360).roundToInt()

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    fun setStartLocation(startLatLng: LatLng) {
        this.start = startLatLng
    }

    fun setEndLocation(endLatLng: LatLng) {
        this.end = endLatLng
    }

    companion object {

        fun <T> throttleLatest(
            coroutineScope: CoroutineScope,
            destinationFunction: (T) -> Unit,
            intervalMs: Long = 500L,
        ): (T) -> Unit {
            var throttleJob: Job? = null
            var latestParam: T
            return { param: T ->
                latestParam = param
                if (throttleJob?.isCompleted != false) {
                    throttleJob = coroutineScope.launch {
                        delay(intervalMs)
                        latestParam.let(destinationFunction)
                    }
                }
            }
        }
    }
}
