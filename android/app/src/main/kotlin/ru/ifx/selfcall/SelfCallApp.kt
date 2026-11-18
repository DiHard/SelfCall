package ru.ifx.selfcall

import android.app.Application
import io.livekit.android.LiveKit

class SelfCallApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Нужно для предпросмотра камеры до входа во встречу
        LiveKit.init(this)
    }
}
