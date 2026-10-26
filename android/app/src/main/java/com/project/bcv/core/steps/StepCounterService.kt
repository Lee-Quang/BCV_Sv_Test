package com.project.bcv.core.steps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.project.bcv.R
import com.project.bcv.feature.account.auth.presentation.AccountActivity

/** Keeps the low-power hardware counter registered while an active journey is being tracked. */
class StepCounterService : Service(), SensorEventListener {
    private lateinit var tracker: DeviceStepTracker
    private lateinit var sensors: SensorManager
    private lateinit var worker: HandlerThread
    private var registered = false
    private var bootCount = -1

    override fun onCreate() {
        super.onCreate()
        tracker = DeviceStepTracker.get(this)
        sensors = getSystemService(SensorManager::class.java)
        worker = HandlerThread("BCV-step-counter").apply { start() }
        bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Đếm bước chân", NotificationManager.IMPORTANCE_LOW)
        )
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else startForeground(NOTIFICATION_ID, notification())
            running = true
        } catch (error: RuntimeException) {
            tracker.failure = "Chưa bật được bộ đếm bước. Hãy mở lại ứng dụng và kiểm tra quyền hoạt động thể chất."
            Log.w(TAG, "Could not start the step-counting service", error)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running || !tracker.state().active || !DeviceStepTracker.hasPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!registered || intent?.getBooleanExtra(RESET_LISTENER, false) == true) {
            sensors.unregisterListener(this)
            val counter = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            registered = counter != null && sensors.registerListener(
                this, counter, SensorManager.SENSOR_DELAY_NORMAL, 0, Handler(worker.looper)
            )
            if (!registered) {
                tracker.failure = "Chưa đọc được cảm biến bước chân của điện thoại."
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!tracker.state().active || !DeviceStepTracker.hasPermission(this)) {
            stopSelf()
            return
        }
        val raw = event.values.firstOrNull() ?: return
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER || !raw.isFinite() || raw < 0) return
        val occurredAt = (System.currentTimeMillis() - SystemClock.elapsedRealtime() + event.timestamp / 1_000_000L) / 1_000L
        try {
            tracker.record(raw.toLong(), bootCount, event.timestamp, occurredAt)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        } catch (error: RuntimeException) {
            tracker.failure = "Chưa lưu được số bước trên điện thoại. Hãy thử mở lại ứng dụng."
            Log.w(TAG, "Could not persist a step-counter reading", error)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensors.unregisterListener(this)
        worker.quitSafely()
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, AccountActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_directions_walk)
            .setContentTitle("BCV đang đếm bước chân")
            .setContentText("Đã ghi nhận ${tracker.state().totalSteps} bước cho hành trình hiện tại.")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "StepCounterService"
        private const val CHANNEL = "journey_steps"
        private const val NOTIFICATION_ID = 2001
        private const val RESET_LISTENER = "reset_listener"
        @Volatile private var running = false

        fun start(context: Context, resetListener: Boolean = false) {
            if (!DeviceStepTracker.hasPermission(context) || !DeviceStepTracker.get(context).state().active) return
            if (running && !resetListener) return
            val tracker = DeviceStepTracker.get(context)
            tracker.failure = null
            try {
                ContextCompat.startForegroundService(context,
                    Intent(context, StepCounterService::class.java).putExtra(RESET_LISTENER, resetListener))
            } catch (error: RuntimeException) {
                tracker.failure = "Chưa bật được bộ đếm bước. Hãy mở lại ứng dụng để tiếp tục."
                Log.w(TAG, "Could not request the step-counting service", error)
            }
        }
    }
}
