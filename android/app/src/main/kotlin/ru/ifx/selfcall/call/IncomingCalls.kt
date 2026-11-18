package ru.ifx.selfcall.call

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.ifx.selfcall.MainActivity
import ru.ifx.selfcall.R
import ru.ifx.selfcall.data.Api
import ru.ifx.selfcall.data.Prefs
import java.util.UUID

internal object IncomingCalls {
    const val CHANNEL = "incoming_calls"
    const val EXTRA_ID = "direct_call_id"
    const val EXTRA_PROOF = "direct_call_proof"
    const val OPEN = "ru.ifx.selfcall.OPEN_CALL"
    const val ACCEPT = "ru.ifx.selfcall.ACCEPT_CALL"
    private val current = MutableStateFlow<String?>(null)
    val incoming = current.asStateFlow()
    private val dismissed = android.util.LruCache<String, Boolean>(32)

    fun createChannel(context: Context) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build()
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(sound, attributes)
                enableVibration(true)
                setShowBadge(false)
            },
        )
    }

    @SuppressLint("MissingPermission") // Android still checks the permission if it was revoked meanwhile.
    @Synchronized
    fun show(context: Context, id: String, name: String, timeout: Long) {
        if (dismissed.get(id) == true) return
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE
        ) return

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        fun activity(action: String) = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java)
                .setAction(action).setData(Uri.parse("selfcall-call:$id"))
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_PROOF, Prefs(context).callNotificationProof), flags,
        )
        val open = activity(OPEN)
        val decline = PendingIntent.getBroadcast(
            context, 0, Intent(context, DeclineCallReceiver::class.java)
                .setData(Uri.parse("selfcall-call:$id")).putExtra(EXTRA_ID, id), flags,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Входящий звонок")
            .setContentText(name)
            .setContentIntent(open)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(
                Person.Builder().setName(name).setImportant(true).build(), decline, activity(ACCEPT),
            ))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(timeout)
        if (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()) {
            notification.setFullScreenIntent(open, true)
        }
        try {
            manager.notify(id, 2, notification.build().apply {
                this.flags = this.flags or android.app.Notification.FLAG_INSISTENT
            })
            current.value = id
        } catch (_: SecurityException) {
            // Разрешение могло измениться между проверкой и показом уведомления.
        }
    }

    @Synchronized
    fun dismiss(context: Context, id: String) {
        dismissed.put(id, true)
        context.getSystemService(NotificationManager::class.java).cancel(id, 2)
        current.compareAndSet(id, null)
    }

    fun parseId(raw: String?): String? = raw?.let {
        runCatching { UUID.fromString(it).toString() }.getOrNull()?.takeIf { id -> id == it }
    }
}

class CallMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val id = IncomingCalls.parseId(message.data["call_id"]) ?: return
        when (message.data["type"]) {
            "incoming_call" -> {
                if (Prefs(this).incomingName == null) return
                val name = message.data["name"]?.take(128)?.takeIf { it.isNotBlank() } ?: return
                val expires = message.data["expires_at_ms"]?.toLongOrNull() ?: return
                val remaining = (expires - System.currentTimeMillis()).coerceAtMost(35_000)
                if (remaining > 0) IncomingCalls.show(this, id, name, remaining)
            }
            "cancelled_call" -> IncomingCalls.dismiss(this, id)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        val prefs = Prefs(this)
        if (prefs.incomingName == null) return
        runBlocking {
            runCatching { Api(applicationContext).refreshIncoming(prefs.callsDeviceId, token) }
        }
    }
}

class DeclineCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = IncomingCalls.parseId(intent.getStringExtra(IncomingCalls.EXTRA_ID)) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Api(context.applicationContext).changeDirectCall(id, "decline")
                IncomingCalls.dismiss(context, id)
            } catch (_: Exception) {
                // Уведомление остаётся доступно, если соединение пропало.
            } finally {
                pending.finish()
            }
        }
    }
}
