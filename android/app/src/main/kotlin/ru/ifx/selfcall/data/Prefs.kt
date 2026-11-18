package ru.ifx.selfcall.data

import android.content.Context
import java.util.UUID

/** Что человек выбрал в прошлый раз: имя и включены ли микрофон и камера. */
class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("selfcall", Context.MODE_PRIVATE)

    private val profileName: String?
        get() = incomingName ?: prefs.getString("lastCallName", null)

    val rememberedMeetingName: String?
        get() = prefs.getString("meetingName", null)
            ?: if (profileName == null) prefs.getString("name", null)?.takeIf { it.isNotBlank() } else null

    val initialMeetingName: String
        get() = limitMeetingName(rememberedMeetingName ?: profileName.orEmpty())

    fun saveMeetingName(name: String?) {
        prefs.edit().remove("name").putString("meetingName", name?.trim()?.takeIf { it.isNotEmpty() }).apply()
    }

    var micOn: Boolean
        get() = prefs.getBoolean("micOn", true)
        set(value) = prefs.edit().putBoolean("micOn", value).apply()

    var camOn: Boolean
        get() = prefs.getBoolean("camOn", true)
        set(value) = prefs.edit().putBoolean("camOn", value).apply()

    val callsDeviceId: String
        get() = persistentId("callsDeviceId")

    internal val callNotificationProof: String
        get() = persistentId("callNotificationProof")

    private fun persistentId(key: String): String = synchronized(DEVICE_ID_LOCK) {
        prefs.getString(key, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(key, it).commit()
        }
    }

    var incomingName: String?
        get() = prefs.getString("incomingName", null)
        set(value) = prefs.edit().putString("incomingName", value).apply()

    var lastCallName: String
        get() = profileName ?: rememberedMeetingName.orEmpty()
        set(value) = prefs.edit().putString("lastCallName", value).apply()

    internal companion object {
        private val DEVICE_ID_LOCK = Any()

        fun limitMeetingName(name: String): String {
            val characters = name.codePointCount(0, name.length).coerceAtMost(40)
            return name.substring(0, name.offsetByCodePoints(0, characters))
        }
    }
}
