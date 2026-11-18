package ru.ifx.selfcall.call

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.room.Room
import io.livekit.android.util.flow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest

private val earpiecePreference = listOf(
    AudioDevice.BluetoothHeadset::class.java,
    AudioDevice.WiredHeadset::class.java,
    AudioDevice.Earpiece::class.java,
    AudioDevice.Speakerphone::class.java,
)

internal suspend fun Room.routeAudioByProximity(context: Context) {
    val audio = audioSwitchHandler ?: return
    val sensors = context.getSystemService(SensorManager::class.java) ?: return
    val proximity = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return
    // Как при гашении экрана в Android: до 5 см, с учётом датчиков «близко/далеко».
    val nearThreshold = minOf(5f, proximity.maximumRange)

    localParticipant::isCameraEnabled.flow.collectLatest { cameraOn ->
        if (cameraOn) return@collectLatest
        val defaultPreference = audio.preferredDeviceList
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val distance = event.values[0]
                audio.preferredDeviceList = if (distance >= 0f && distance < nearThreshold) {
                    earpiecePreference
                } else {
                    defaultPreference
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        if (!sensors.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_NORMAL)) return@collectLatest
        try {
            awaitCancellation()
        } finally {
            sensors.unregisterListener(listener)
            audio.preferredDeviceList = defaultPreference
        }
    }
}
