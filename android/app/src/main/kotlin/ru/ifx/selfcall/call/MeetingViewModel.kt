package ru.ifx.selfcall.call

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import ru.ifx.selfcall.data.AndroidUpdatePolicy
import ru.ifx.selfcall.data.Api
import ru.ifx.selfcall.data.ApiException
import ru.ifx.selfcall.data.CallContact
import ru.ifx.selfcall.data.CallDirectory
import ru.ifx.selfcall.data.DirectCall
import ru.ifx.selfcall.data.JoinResponse
import ru.ifx.selfcall.data.MeetingInfo
import ru.ifx.selfcall.data.Prefs
import ru.ifx.selfcall.update.AppUpdates
import java.util.UUID
import kotlin.coroutines.resume

enum class LeaveReason { LEFT, ENDED, LOST }

/** Текущий экран приложения. */
sealed interface Screen {
    data class Home(val creating: Boolean = false, val error: String? = null) : Screen
    data class SignIn(val submitting: Boolean = false, val error: String? = null) : Screen
    data class Checking(val id: String) : Screen
    data class PreJoin(
        val info: MeetingInfo,
        val justCreated: Boolean,
        val joining: Boolean = false,
        val error: String? = null,
    ) : Screen
    data class InCall(val id: String, val room: Room, val startedAt: Long?,
                      val direct: DirectCall? = null) : Screen
    data class Calls(val directory: CallDirectory? = null, val busy: Boolean = true,
                     val error: String? = null) : Screen
    data class IncomingSetup(
        val directory: CallDirectory,
        val name: String,
        val enabling: Boolean = false,
        val notificationsBlocked: Boolean = false,
        val error: String? = null,
    ) : Screen
    data class Direct(val call: DirectCall, val busy: Boolean = false,
                      val error: String? = null) : Screen
    data class Left(val id: String, val reason: LeaveReason) : Screen
    data class Failed(val id: String, val title: String, val text: String, val retry: Boolean) : Screen
}

class MeetingViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val api = Api(app, ::requireAppUpdate)
    internal val updates: AppUpdates = AppUpdates(app, viewModelScope, api, ::requestSignIn)

    var screen by mutableStateOf<Screen>(Screen.Home())
        private set
    var openingIncoming by mutableStateOf(false)
        private set

    private var room: Room? = null
    private var roomEvents: Job? = null
    private var loadJob: Job? = null
    private var afterSignIn: (() -> Unit)? = null
    private var callPoll: Job? = null

    /** Камера была включена, когда приложение ушло в фон: вернуть её, когда вернётся. */
    private var camPausedInBackground = false

    val inCall get() = screen is Screen.InCall
    val callInProgress get() = inCall || (screen as? Screen.Direct)?.call?.status in setOf("ringing", "accepted")
    internal val keepCallScreen get() = inCall || (screen as? Screen.PreJoin)?.joining == true ||
        (screen as? Screen.Direct)?.busy == true

    private fun requireAppUpdate(policy: AndroidUpdatePolicy) {
        updates.requireUpdate(policy)
    }

    fun signIn(login: String, password: String) {
        val current = screen as? Screen.SignIn ?: return
        if (current.submitting) return
        if (':' in login) {
            screen = current.copy(error = "Проверьте логин: в нём не должно быть двоеточия.")
            return
        }
        screen = current.copy(submitting = true, error = null)
        viewModelScope.launch {
            try {
                api.signIn(login, password)
            } catch (e: ApiException) {
                screen = Screen.SignIn(error = e.message)
                return@launch
            }
            val resume = afterSignIn
            afterSignIn = null
            screen = Screen.Home()
            updates.refresh()
            resume?.invoke()
        }
    }

    private fun requestSignIn(resume: () -> Unit) {
        afterSignIn = resume
        screen = Screen.SignIn()
    }

    // ----- Главная -----

    fun createMeeting() {
        if (updates.blocksCalls) return
        val s = screen as? Screen.Home ?: return
        if (s.creating) return
        screen = s.copy(creating = true, error = null)
        loadJob = viewModelScope.launch {
            val id = try {
                api.createMeeting().id
            } catch (e: ApiException) {
                if (e.code == "sign_in_required") {
                    requestSignIn(::createMeeting)
                } else {
                    screen = Screen.Home(error = CREATE_ERRORS[e.code] ?: "Не получилось создать встречу. Попробуйте ещё раз.")
                }
                return@launch
            }
            load(id, justCreated = true)
        }
    }

    fun goHome() {
        if (inCall) return
        callPoll?.cancel()
        loadJob?.cancel()
        screen = Screen.Home()
    }

    fun openDirectory() {
        if (updates.blocksCalls || inCall || (screen as? Screen.PreJoin)?.joining == true) return
        callPoll?.cancel()
        loadJob?.cancel()
        screen = Screen.Calls()
        loadJob = viewModelScope.launch { loadDirectory() }
    }

    private suspend fun loadDirectory() {
        try {
            val deviceId = prefs.callsDeviceId
            val directory = api.callDirectory(deviceId)
            val activeName = directory.incoming?.name
            prefs.incomingName = activeName
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = activeName != null
            screen = Screen.Calls(directory, busy = false)
            if (activeName != null) {
                val token = withTimeoutOrNull(5_000) { messaging.registrationToken() }
                if (token != null) api.refreshIncoming(deviceId, token)
            }
        } catch (e: ApiException) {
            callsError(e, ::openDirectory)
        }
    }

    fun startIncomingSetup() {
        val current = screen as? Screen.Calls ?: return
        val directory = current.directory ?: return
        if (current.busy || directory.incoming != null) return
        screen = Screen.IncomingSetup(directory, prefs.lastCallName)
    }

    fun closeIncomingSetup() {
        val current = screen as? Screen.IncomingSetup ?: return
        if (!current.enabling) screen = Screen.Calls(current.directory, busy = false)
    }

    fun editIncomingName(name: String) {
        val current = screen as? Screen.IncomingSetup ?: return
        if (current.enabling) return
        val characters = name.codePointCount(0, name.length).coerceAtMost(80)
        screen = current.copy(name = name.substring(0, name.offsetByCodePoints(0, characters)), error = null)
    }

    fun enableIncoming() {
        if (updates.blocksCalls) return
        val current = screen as? Screen.IncomingSetup ?: return
        val displayName = current.name.trim()
        if (current.enabling || displayName.isBlank()) return
        screen = current.copy(enabling = true, notificationsBlocked = false, error = null)
        loadJob = viewModelScope.launch {
            try {
                val messaging = FirebaseMessaging.getInstance()
                val token = withTimeoutOrNull(30_000) { messaging.registrationToken() }
                    ?: throw ApiException("firebase_unavailable", "Не удалось включить входящие звонки. Проверьте интернет и попробуйте ещё раз.")
                api.enableIncoming(prefs.callsDeviceId, displayName, token)
                prefs.incomingName = displayName
                prefs.lastCallName = displayName
                messaging.isAutoInitEnabled = true
            } catch (e: ApiException) {
                val draft = current.copy(notificationsBlocked = false, error = null)
                if (e.code == "sign_in_required") {
                    requestSignIn { screen = draft }
                } else {
                    screen = draft.copy(error = when (e.code) {
                        "unknown" -> "Не удалось включить входящие звонки. Попробуйте ещё раз."
                        "calls_unavailable" -> "Входящие звонки сейчас недоступны. Попробуйте позже."
                        else -> callError(e)
                    })
                }
                return@launch
            }
            screen = Screen.Calls()
            loadDirectory()
        }
    }

    fun incomingPermissionDenied() {
        val current = screen as? Screen.IncomingSetup ?: return
        if (!current.enabling) screen = current.copy(notificationsBlocked = true, error = null)
    }

    fun disableIncoming() {
        val current = screen as? Screen.Calls ?: return
        if (current.busy) return
        screen = current.copy(busy = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                api.disableIncoming(prefs.callsDeviceId)
                prefs.incomingName = null
                FirebaseMessaging.getInstance().isAutoInitEnabled = false
                loadDirectory()
            } catch (e: ApiException) {
                callsError(e, ::openDirectory)
            }
        }
    }

    private fun callsError(error: ApiException, resume: () -> Unit) {
        if (error.code == "sign_in_required") requestSignIn(resume)
        else (screen as? Screen.Calls)?.let {
            screen = it.copy(busy = false, error = callError(error))
        }
    }

    fun dial(contact: CallContact) {
        if (updates.blocksCalls) return
        val current = screen as? Screen.Calls ?: return
        if (current.busy) return
        screen = current.copy(busy = true, error = null)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                showDirect(api.dial(UUID.randomUUID().toString(), contact.login))
            } catch (e: ApiException) {
                callsError(e, ::openDirectory)
            }
        }
    }

    fun showIncoming(id: String, accept: Boolean = false) {
        if (updates.blocksCalls && !inCall) {
            IncomingCalls.dismiss(getApplication(), id)
            return
        }
        val active = screen
        if (inCall || (active as? Screen.PreJoin)?.joining == true || active is Screen.SignIn) return
        if (active is Screen.Direct && active.call.id == id) {
            if (accept) answerDirect()
            return
        }
        if (callInProgress || (active as? Screen.Direct)?.busy == true) return
        loadJob?.cancel()
        openingIncoming = true
        loadJob = viewModelScope.launch {
            try {
                val call = api.getDirectCall(id)
                if (!call.incoming || call.status != "ringing") {
                    IncomingCalls.dismiss(getApplication(), id)
                    return@launch
                }
                showDirect(call)
                if (accept) answerDirect()
            } catch (e: ApiException) {
                if (e.code == "sign_in_required") requestSignIn { showIncoming(id, accept) }
                else if (e.code in setOf("call_not_found", "update_required")) IncomingCalls.dismiss(getApplication(), id)
                else if (accept || screen is Screen.Home) screen = Screen.Home(error = callError(e))
            } finally {
                openingIncoming = false
            }
        }
    }

    private fun showDirect(call: DirectCall) {
        screen = Screen.Direct(call)
        if (call.status == "ringing") watchDirect(call)
    }

    private fun watchDirect(call: DirectCall) {
        callPoll?.cancel()
        callPoll = viewModelScope.launch {
            val deadline = SystemClock.elapsedRealtime() + call.remainingMs.coerceIn(0, 35_000) + 2_000
            while (true) {
                delay(1_300)
                val current = screen as? Screen.Direct ?: return@launch
                if (current.busy || current.call.status != "ringing") return@launch
                val latest = try {
                    api.getDirectCall(call.id)
                } catch (e: ApiException) {
                    if (e.code == "network" && SystemClock.elapsedRealtime() < deadline) continue
                    screen = current.copy(call = current.call.copy(status = "ended"), error = callError(e))
                    IncomingCalls.dismiss(getApplication(), call.id)
                    return@launch
                }
                if (latest.status != "ringing") {
                    screen = Screen.Direct(latest)
                    IncomingCalls.dismiss(getApplication(), call.id)
                    if (latest.status == "accepted" && !latest.incoming) connectDirect(latest)
                    return@launch
                }
            }
        }
    }

    fun answerDirect() = changeDirect("accept")

    fun closeDirect() {
        val current = screen as? Screen.Direct ?: return
        if (current.busy) return
        if (current.call.status == "ringing") {
            changeDirect(if (current.call.incoming) "decline" else "cancel")
        } else if (current.call.status == "accepted") {
            changeDirect("end")
        } else openDirectory()
    }

    private fun changeDirect(action: String) {
        val current = screen as? Screen.Direct ?: return
        if (current.busy || (action == "accept" && (updates.blocksCalls || current.call.status != "ringing"))) return
        callPoll?.cancel()
        screen = current.copy(busy = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                val latest = api.changeDirectCall(current.call.id, action)
                IncomingCalls.dismiss(getApplication(), latest.id)
                if (action == "accept" && latest.status == "accepted") connectDirect(latest)
                else if (action == "accept") showDirect(latest)
                else openDirectory()
            } catch (e: ApiException) {
                screen = current.copy(busy = false, error = callError(e))
                if (current.call.status == "ringing") watchDirect(current.call)
            }
        }
    }

    private suspend fun connectDirect(call: DirectCall) {
        screen = Screen.Direct(call, busy = true)
        try {
            val conn = api.connectDirectCall(call.id)
            connectRoom(conn, micOn = true, camOn = false, direct = call)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            screen = Screen.Direct(call, error = "Не удалось подключиться к звонку. Позвоните ещё раз.")
        }
    }

    // Existing installations and Google Play services use FCM registration tokens.
    @Suppress("DEPRECATION")
    private suspend fun FirebaseMessaging.registrationToken(): String? = awaitToken(token)

    private suspend fun awaitToken(task: Task<String>): String? = suspendCancellableCoroutine { continuation ->
        task.addOnCompleteListener { result -> continuation.resume(if (result.isSuccessful) result.result else null) }
    }

    private fun callError(error: ApiException): String = when (error.code) {
        "network" -> "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз."
        "unknown" -> "Не удалось открыть звонки. Обновите список или попробуйте позже."
        else -> error.message ?: "Не удалось выполнить действие. Попробуйте ещё раз."
    }

    // ----- Встреча -----

    /** Открыть встречу по коду: проверить ссылку и показать экран перед входом. */
    fun open(id: String, justCreated: Boolean = false) {
        if (updates.blocksCalls || callInProgress) return
        if (screen is Screen.SignIn) {
            afterSignIn = { open(id, justCreated) }
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load(id, justCreated) }
    }

    private suspend fun load(id: String, justCreated: Boolean) {
        screen = Screen.Checking(id)
        try {
            screen = Screen.PreJoin(api.getMeeting(id), justCreated)
        } catch (e: ApiException) {
            when (e.code) {
                "sign_in_required" -> requestSignIn { open(id, justCreated) }
                "meeting_not_found" -> screen = notFound(id)
                else -> screen = Screen.Failed(id, "Нет связи с сервером", "Проверьте интернет и попробуйте ещё раз.", retry = true)
            }
        }
    }

    fun retry() {
        when (val s = screen) {
            is Screen.Failed -> open(s.id)
            is Screen.Left -> open(s.id)
            else -> Unit
        }
    }

    fun join(name: String, rememberName: Boolean, micOn: Boolean, camOn: Boolean) {
        if (updates.blocksCalls) return
        val s = screen as? Screen.PreJoin ?: return
        if (s.joining) return
        screen = s.copy(joining = true, error = null)
        viewModelScope.launch {
            val conn = try {
                api.joinMeeting(s.info.id, name)
            } catch (e: ApiException) {
                if (e.code == "sign_in_required") {
                    requestSignIn { open(s.info.id, s.justCreated) }
                } else {
                    screen = s.copy(joining = false, error = JOIN_ERRORS[e.code] ?: "Не получилось войти. Попробуйте ещё раз.")
                }
                return@launch
            }
            try {
                connectRoom(conn, micOn, camOn)
                prefs.saveMeetingName(name.takeIf { rememberName })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                screen = s.copy(joining = false, error = "Не получилось подключиться к встрече. Проверьте интернет и попробуйте ещё раз.")
            }
        }
    }

    private suspend fun connectRoom(conn: JoinResponse, micOn: Boolean, camOn: Boolean,
                                    direct: DirectCall? = null) {
        val startedAt = conn.elapsedMs?.let { SystemClock.elapsedRealtime() - it }
        val r = LiveKit.create(getApplication(), overrides = LiveKitOverrides(okHttpClient = api.signalingClient))
        try {
            r.connect(conn.url, conn.token)
        } catch (e: Exception) {
            r.release()
            throw e
        }
        room = r
        roomEvents = viewModelScope.launch {
            launch { r.routeAudioByProximity(getApplication()) }
            r.events.collect { event ->
                if (event is RoomEvent.Disconnected) onDisconnected(conn.meetingId, event.reason)
                if (direct != null && event is RoomEvent.ParticipantDisconnected) r.disconnect()
            }
        }
        screen = Screen.InCall(conn.meetingId, r, startedAt, direct)
        CallService.start(getApplication(), conn.meetingId, direct?.name)
        if (micOn && granted(Manifest.permission.RECORD_AUDIO)) {
            runCatching { r.localParticipant.setMicrophoneEnabled(true) }
        }
        if (camOn && granted(Manifest.permission.CAMERA)) {
            runCatching { r.localParticipant.setCameraEnabled(true) }
        }
    }

    fun leave() {
        (screen as? Screen.InCall)?.direct?.let { call ->
            viewModelScope.launch { runCatching { api.changeDirectCall(call.id, "end") } }
        }
        room?.disconnect() // придёт событие Disconnected с причиной CLIENT_INITIATED
    }

    private fun onDisconnected(id: String, reason: DisconnectReason) {
        val direct = (screen as? Screen.InCall)?.direct
        val leaveReason = when (reason) {
            DisconnectReason.CLIENT_INITIATED -> LeaveReason.LEFT
            DisconnectReason.ROOM_DELETED -> LeaveReason.ENDED
            else -> LeaveReason.LOST
        }
        closeRoom()
        if (direct != null) {
            screen = Screen.Direct(direct.copy(status = "ended"),
                error = if (leaveReason == LeaveReason.LOST) "Связь прервалась. Позвоните ещё раз." else null)
        } else screen = Screen.Left(id, leaveReason)
    }

    private fun closeRoom() {
        roomEvents?.cancel()
        roomEvents = null
        room?.let {
            it.disconnect()
            it.release()
        }
        room = null
        camPausedInBackground = false
        CallService.stop(getApplication())
    }

    // ----- Управление во время звонка -----

    fun setMic(on: Boolean) {
        val r = room ?: return
        prefs.micOn = on
        viewModelScope.launch { runCatching { r.localParticipant.setMicrophoneEnabled(on) } }
    }

    fun setCam(on: Boolean) {
        val r = room ?: return
        prefs.camOn = on
        viewModelScope.launch { runCatching { r.localParticipant.setCameraEnabled(on) } }
    }

    fun switchCamera() {
        val track = room?.localParticipant?.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
        track?.switchCamera()
    }

    /**
     * В фоне Android не отдаёт камеру приложению, поэтому выключаем её сами и включаем,
     * когда приложение снова на экране. Микрофон и звук продолжают работать.
     */
    fun onAppBackground() {
        val r = room ?: return
        if (r.localParticipant.isCameraEnabled) {
            camPausedInBackground = true
            viewModelScope.launch { runCatching { r.localParticipant.setCameraEnabled(false) } }
        }
    }

    fun onAppForeground() {
        val r = room ?: return
        if (camPausedInBackground) {
            camPausedInBackground = false
            viewModelScope.launch { runCatching { r.localParticipant.setCameraEnabled(true) } }
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        closeRoom()
    }

    private fun notFound(id: String) = Screen.Failed(
        id,
        "Встреча не найдена",
        "Проверьте ссылку: возможно, в ней опечатка или она скопирована не полностью.",
        retry = false,
    )

    companion object {
        private val CREATE_ERRORS = mapOf(
            "server_busy" to "Сейчас на сервере идёт максимум встреч. Попробуйте через несколько минут.",
            "too_many_requests" to "С этого устройства создано слишком много встреч. Попробуйте через час.",
            "network" to "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз.",
        )
        private val JOIN_ERRORS = mapOf(
            "meeting_full" to "Во встрече нет свободных мест. Попробуйте войти, когда кто-нибудь выйдет.",
            "server_busy" to "Сейчас на сервере идёт максимум встреч. Попробуйте через несколько минут.",
            "too_many_requests" to "Слишком много попыток входа. Подождите минуту.",
            "network" to "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз.",
        )
    }
}
