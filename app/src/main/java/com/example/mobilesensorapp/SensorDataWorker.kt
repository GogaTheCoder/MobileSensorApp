package com.example.mobilesensorapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlin.math.log10
import java.io.File
import java.util.*
import androidx.core.content.edit

class SensorDataWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var accelData = FloatArray(3)
    private var heartRate = -1f
    private var steps: Float = -1f


    override suspend fun doWork(): Result {
        Log.d("SensorWorker", "doWork запущен")

        collectSensorReadings()
        val noiseDb = measureNoise()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .getString("local_uid", null)
            ?: UUID.randomUUID().toString().also {
                applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                    .edit { putString("local_uid", it) }
            }

        val data = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "accel" to accelData.toList(),
            "heartRate" to heartRate,
            "noiseDb" to noiseDb,
            "steps" to steps,
            "source" to "worker"
        )

        try {
            FirebaseDatabase.getInstance()
                .getReference("sensorData")
                .child(uid)
                .push()
                .setValue(data)
            Log.d("SensorWorker", "Данные отправлены: $data")
            return Result.success()
        } catch (e: Exception) {
            Log.e("SensorWorker", "Ошибка отправки данных: ${e.message}", e)
            return Result.retry()
        }
    }

    private suspend fun collectSensorReadings() {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val heart = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (accel != null) {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (heart != null) {
            sensorManager.registerListener(this, heart, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val step = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (step != null) {
            sensorManager.registerListener(this, step, SensorManager.SENSOR_DELAY_NORMAL)
        }



        // Подождём 2 секунды для получения данных
        delay(2000)

        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accelData = event.values.clone()
            Sensor.TYPE_HEART_RATE -> heartRate = event.values.firstOrNull() ?: -1f
            Sensor.TYPE_STEP_COUNTER -> steps = event.values.firstOrNull() ?: -1f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun measureNoise(): Double {
        return try {
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                val outputFile = File(applicationContext.cacheDir, "temp_worker.3gp").absolutePath
                setOutputFile(outputFile)
                prepare()
                start()
            }

            Thread.sleep(2000)

            val amp = recorder.maxAmplitude
            recorder.stop()
            recorder.release()
            20 * log10(amp.toDouble().coerceAtLeast(1.0))
        } catch (e: Exception) {
            Log.e("SensorWorker", "Ошибка записи шума: ${e.message}", e)
            -1.0
        }
    }
}
