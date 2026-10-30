package app.classbridgeai.ClassBridge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import app.classbridgeai.ClassBridge.data.services.HomeworkNotificationWorker
import app.classbridgeai.ClassBridge.data.services.NotificationScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class ClassBridgeApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate called")
        
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                Log.d(TAG, "Initializing Firebase")
                FirebaseApp.initializeApp(this)
                Log.d(TAG, "Firebase initialized successfully")
            } else {
                Log.d(TAG, "Firebase already initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
        }
        
        val channel = NotificationChannel(
            HomeworkNotificationWorker.CHANNEL_ID,
            HomeworkNotificationWorker.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily reminders for upcoming homework"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        NotificationScheduler.scheduleIfNotRunning(this)

        Log.d(TAG, "Application initialization complete")
    }
    
    companion object {
        private const val TAG = "ClassBridgeApp"
    }
}
