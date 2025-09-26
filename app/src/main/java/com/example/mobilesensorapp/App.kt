package com.example.mobilesensorapp

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()

//        FirebaseAuth.getInstance().signInAnonymously()
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser == null) {
                FirebaseAuth.getInstance().signInAnonymously()
            }
        }

        val workRequest = PeriodicWorkRequestBuilder<SensorDataWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SensorDataUpload",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
