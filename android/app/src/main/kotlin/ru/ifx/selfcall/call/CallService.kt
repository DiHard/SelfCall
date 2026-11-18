package ru.ifx.selfcall.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ru.ifx.selfcall.MainActivity
import ru.ifx.selfcall.R

/**
 * Служба на время звонка. Пока она работает, Android не останавливает приложение в фоне
 * и не глушит микрофон: можно свернуть звонок, открыть мессенджер или выключить экран.
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val meetingId = intent?.getStringExtra(EXTRA_ID).orEmpty()
        val directName = intent?.getStringExtra(EXTRA_DIRECT_NAME)
        ensureChannel()

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (directName == null) "Идёт встреча" else "Идёт звонок")
            .setContentText("${directName ?: meetingId} · нажмите, чтобы вернуться")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && micGranted()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            // Android может запретить запуск, например если приложение уже ушло в фон.
            // Звонок при этом идёт, просто без защиты от остановки в фоне.
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun micGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Идущая встреча", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Показывается, пока вы во встрече"
                    setShowBadge(false)
                },
            )
        }
    }

    companion object {
        private const val CHANNEL = "call"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_ID = "meetingId"
        private const val EXTRA_DIRECT_NAME = "directName"

        fun start(context: Context, meetingId: String, directName: String? = null) {
            val intent = Intent(context, CallService::class.java)
                .putExtra(EXTRA_ID, meetingId).putExtra(EXTRA_DIRECT_NAME, directName)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}
