package com.paranormal.toolkit.features.ghostapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

data class TriggerTick(val energy: Float, val atMillis: Long)

/**
 * Turns raw motion / magnetic-field jitter into unpredictable "tick" events.
 *
 * This class only ever decides *when* a tick happens. A tick carries a
 * timestamp and the measured energy and nothing else - no sensor value is
 * ever passed on in a form that could identify an entry in the word
 * database, and nothing in here touches that database. Which entry gets
 * spoken is drawn in [GhostAppService] from a [SecureRandom] that has never
 * seen any sensor data, so a sensor reading cannot steer the word choice,
 * deliberately or accidentally.
 */
class SensorTriggerEngine(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val secureRandom = SecureRandom()

    private var lastAccel: FloatArray? = null
    private var lastMag: FloatArray? = null
    private var accelBaseline = 0f
    private var magBaseline = 0f

    private var lastTickAtMillis = 0L
    private var nextCooldownMillis = 3_000L
    private var nextThreshold = 1f

    /** 0f (insensitive) .. 1f (very sensitive); driven by the UI slider. */
    var sensitivity: Float = 0.5f

    private val _ticks = MutableSharedFlow<TriggerTick>(extraBufferCapacity = 8)
    val ticks: SharedFlow<TriggerTick> = _ticks

    val hasSensors: Boolean get() = accelerometer != null || magnetometer != null

    fun start() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        lastAccel = null
        lastMag = null
        accelBaseline = 0f
        magBaseline = 0f
        lastTickAtMillis = System.currentTimeMillis()
        nextCooldownMillis = randomCooldown()
        nextThreshold = randomThreshold()
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val jitter = when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                val delta = deltaMagnitude(lastAccel, event.values)
                lastAccel = event.values.copyOf()
                accelBaseline = ema(accelBaseline, delta)
                delta - accelBaseline
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val delta = deltaMagnitude(lastMag, event.values)
                lastMag = event.values.copyOf()
                magBaseline = ema(magBaseline, delta)
                delta - magBaseline
            }
            else -> return
        }

        val energy = abs(jitter)
        val now = System.currentTimeMillis()
        if (now - lastTickAtMillis < nextCooldownMillis) return
        if (energy < nextThreshold) return

        lastTickAtMillis = now
        nextCooldownMillis = randomCooldown()
        nextThreshold = randomThreshold()
        _ticks.tryEmit(TriggerTick(energy = energy, atMillis = now))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun deltaMagnitude(previous: FloatArray?, current: FloatArray): Float {
        if (previous == null) return 0f
        var sumSquares = 0f
        for (i in current.indices) {
            val d = current[i] - previous[i]
            sumSquares += d * d
        }
        return sqrt(sumSquares)
    }

    private fun ema(baseline: Float, value: Float, alpha: Float = 0.05f): Float =
        baseline + alpha * (value - baseline)

    /** Randomized window so the trigger timing itself can't be predicted or gamed. */
    private fun randomCooldown(): Long {
        val sensitivityFactor = (1.3f - sensitivity).coerceIn(0.3f, 1.3f)
        val minMs = (3_000 * sensitivityFactor).toLong().coerceAtLeast(1_000)
        val maxMs = (40_000 * sensitivityFactor).toLong().coerceAtLeast(minMs + 1_000)
        val span = maxMs - minMs
        val offset = (secureRandom.nextLong() and Long.MAX_VALUE) % span
        return minMs + offset
    }

    private fun randomThreshold(): Float {
        val base = (1.2f - sensitivity).coerceIn(0.05f, 1.2f)
        val jitterFactor = 0.7f + secureRandom.nextFloat() * 0.6f // 0.7x .. 1.3x
        return base * jitterFactor
    }

    companion object {
        /** Whether this device has any sensor the GhostApp could use as a trigger source. */
        fun deviceHasTriggerSensors(context: Context): Boolean {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            return manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null ||
                manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ||
                manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
        }
    }
}
