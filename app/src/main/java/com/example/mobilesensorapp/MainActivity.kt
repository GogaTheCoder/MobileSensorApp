package com.example.mobilesensorapp

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.util.*
import kotlin.math.log10

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelData = FloatArray(3)
    private var heartRate = -1f
    private var steps: Float = -1f
    private var noiseDb: Float = 0f


    private lateinit var statusText: TextView
    private lateinit var countText: TextView
    private lateinit var lastDataText: TextView
    private lateinit var uidText: TextView

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var uid: String

    private lateinit var tvHeartRate: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvNoise: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvHeartRate = findViewById(R.id.tvHeartRate)
        tvSteps = findViewById(R.id.tvSteps)
        tvNoise = findViewById(R.id.tvNoise)

        statusText = findViewById(R.id.status_text)
        countText = findViewById(R.id.sent_count_text)
        lastDataText = findViewById(R.id.last_data_text)
        uidText = findViewById(R.id.uid_text)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        prefs = getSharedPreferences("prefs", MODE_PRIVATE)

        uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: prefs.getString("local_uid", null)
                    ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("local_uid", it).apply()
            }

        uidText.text = "UID: $uid"
        countText.text = "Отправок: ${prefs.getInt("sent_count", 0)}"

        // Разрешение
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BODY_SENSORS), 1001)

        findViewById<Button>(R.id.button_send_now).setOnClickListener {
            collectAndSendNow()
        }

        findViewById<Button>(R.id.button_run_worker).setOnClickListener {
            val request = OneTimeWorkRequestBuilder<SensorDataWorker>().build()
            WorkManager.getInstance(this).enqueue(request)
            Toast.makeText(this, "Воркер запущен вручную", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.button_clear_data).setOnClickListener {
            FirebaseDatabase.getInstance().getReference("sensorData").child(uid).removeValue()
            Toast.makeText(this, "Данные удалены из Firebase", Toast.LENGTH_SHORT).show()
            statusText.text = "Firebase очищен ❌"
            countText.text = "Отправок: 0"
            prefs.edit().putInt("sent_count", 0).apply()
        }
    }

    private fun collectAndSendNow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Нет разрешения на запись аудио", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
            return
        }

        statusText.text = "Сбор данных..."
        Toast.makeText(this, "Сбор данных начался", Toast.LENGTH_SHORT).show()

        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL)

        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE),
            SensorManager.SENSOR_DELAY_NORMAL)

        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER),
            SensorManager.SENSOR_DELAY_NORMAL)

        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor == null) {
            Log.e("SensorApp", "Шагомер не поддерживается")
        }
        else if (steps == -1f) {
            Toast.makeText(this, "Шаги не считались — пройдись немного", Toast.LENGTH_SHORT).show()
        }



        Handler(Looper.getMainLooper()).postDelayed({
            sensorManager.unregisterListener(this)

            val noiseDb = measureNoise()
            val data = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "accel" to accelData.toList(),
                "heartRate" to heartRate,
                "noiseDb" to noiseDb,
                "steps" to steps,
                "source" to "manual_button"
            )

            FirebaseDatabase.getInstance()
                .getReference("sensorData")
                .child(uid)
                .push()
                .setValue(data)
                .addOnSuccessListener {
                    val count = prefs.getInt("sent_count", 0) + 1
                    prefs.edit().putInt("sent_count", count).apply()
                    statusText.text = "Успешно отправлено ✅"
                    countText.text = "Отправок: $count"
                    lastDataText.text = "Последние данные:\n$data"
                    Toast.makeText(this, "Отправка завершена", Toast.LENGTH_SHORT).show()
                    Log.d("SensorApp", "Данные отправлены: $data")
                }
                .addOnFailureListener { e ->
                    statusText.text = "Ошибка отправки ❌"
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("SensorApp", "Ошибка отправки данных", e)
                }
        }, 2000)
    }

    private fun measureNoise(): Double {
        return try {
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                val outputFile = File(cacheDir, "temp.3gp").absolutePath
                setOutputFile(outputFile)
                prepare()
                start()
            }
            Thread.sleep(2000)
            val amplitude = recorder.maxAmplitude
            recorder.stop()
            recorder.release()

            // Здесь вставляешь:
            noiseDb = if (amplitude > 0) (20 * log10(amplitude.toDouble())).toFloat() else 0f
            tvNoise.text = "Noise: %.2f dB".format(noiseDb)

            noiseDb.toDouble()
        } catch (e: Exception) {
            Log.e("SensorApp", "Ошибка записи шума: ${e.message}", e)
            -1.0
        }
    }


    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accelData = event.values.clone()
            Sensor.TYPE_HEART_RATE -> {
                heartRate = event.values.firstOrNull() ?: -1f
                tvHeartRate.text = "Heart Rate: $heartRate"
            }
            Sensor.TYPE_STEP_COUNTER -> {
                steps = event.values.firstOrNull() ?: -1f
                tvSteps.text = "Steps: $steps"
            }
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
