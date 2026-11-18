package ru.ifx.selfcall.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ifx.selfcall.BuildConfig
import ru.ifx.selfcall.data.AndroidUpdatePolicy
import ru.ifx.selfcall.data.Api
import ru.ifx.selfcall.data.ApiException
import java.io.File
import java.io.IOException

internal enum class UpdatePhase { IDLE, DOWNLOADING, VERIFYING, INSTALLING }

internal data class DownloadProgress(val downloadedBytes: Long, val totalBytes: Long)

internal class AppUpdates(
    context: Context,
    private val scope: CoroutineScope,
    private val api: Api,
    private val requestSignIn: (() -> Unit) -> Unit,
) {
    private val app = context.applicationContext
    private val installer = UpdateInstaller(app)
    private var policy by mutableStateOf(AndroidUpdatePolicy())
    private var dismissedVersion by mutableIntStateOf(0)
    private var checking: Job? = null
    private var downloading: Job? = null
    private var sessionId: Int? = null

    var phase by mutableStateOf(UpdatePhase.IDLE)
        private set
    var downloadProgress by mutableStateOf<DownloadProgress?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var needsInstallPermission by mutableStateOf(false)
        private set
    var confirmation by mutableStateOf<Intent?>(null)
        private set

    val available get() = policy.latest?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    val required get() = BuildConfig.VERSION_CODE < policy.minimumVersionCode
    val offer get() = available?.takeUnless { it.versionCode == dismissedVersion }
    val blocksCalls get() = required || phase != UpdatePhase.IDLE
    val canCancelDownload get() = phase == UpdatePhase.DOWNLOADING || phase == UpdatePhase.VERIFYING

    init {
        scope.launch {
            UpdateInstallReceiver.results.filterNotNull().collect { result ->
                if (result.sessionId != sessionId) return@collect
                if (result.status == PackageInstaller.STATUS_PENDING_USER_ACTION && result.confirmation != null) {
                    confirmation = result.confirmation
                } else {
                    phase = UpdatePhase.IDLE
                    sessionId = null
                    confirmation = null
                    error = when (result.status) {
                        PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> null
                        PackageInstaller.STATUS_FAILURE_STORAGE -> "Освободите место на телефоне и попробуйте ещё раз."
                        else -> "Не удалось установить обновление. Попробуйте ещё раз."
                    }
                }
            }
        }
    }

    fun refresh() {
        if (checking?.isActive == true) return
        checking = scope.launch {
            try {
                policy = api.androidUpdate()
            } catch (_: ApiException) {
                // A network failure or an expired sign-in does not change the last server decision.
            }
        }
    }

    fun requireUpdate(policy: AndroidUpdatePolicy) {
        checking?.cancel()
        this.policy = policy
    }

    fun dismiss() {
        dismissedVersion = offer?.versionCode ?: return
        error = null
        needsInstallPermission = false
    }

    fun askInstallPermission() {
        needsInstallPermission = true
        error = null
    }

    fun installSettingsUnavailable() {
        error = "Откройте настройки Android и разрешите SelfCall устанавливать приложения."
    }

    fun startDownload() {
        val release = available ?: return
        if (phase != UpdatePhase.IDLE) return
        needsInstallPermission = false
        error = null
        phase = UpdatePhase.DOWNLOADING
        downloading = scope.launch {
            var prepared: Int? = null
            var committed = false
            val receivedBytes = MutableStateFlow<Long?>(null)
            fun showProgress(downloaded: Long) {
                downloadProgress = DownloadProgress(downloaded, release.sizeBytes)
                if (downloaded == release.sizeBytes) phase = UpdatePhase.VERIFYING
            }
            val progressReporting = launch {
                receivedBytes.filterNotNull().collect { downloaded ->
                    showProgress(downloaded)
                    delay(200)
                }
            }
            try {
                withContext(Dispatchers.IO) {
                    val apk = File(app.cacheDir, "selfcall-update.apk")
                    try {
                        api.downloadAndroidRelease(release, apk) { downloaded ->
                            receivedBytes.value = downloaded
                        }
                        withContext(Dispatchers.Main.immediate) {
                            progressReporting.cancel()
                            showProgress(release.sizeBytes)
                        }
                        prepared = installer.prepare(apk, release)
                    } finally {
                        apk.delete()
                    }
                }
                if (release.versionCode < policy.minimumVersionCode) {
                    throw ApiException("release_not_found", "Сборка сменилась")
                }
                val id = checkNotNull(prepared)
                sessionId = id
                phase = UpdatePhase.INSTALLING
                installer.commit(id)
                committed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ApiException) {
                when (failure.code) {
                    "sign_in_required" -> requestSignIn(::startDownload)
                    "release_not_found" -> {
                        error = "Сборка сменилась. Попробуйте скачать обновление ещё раз."
                        refresh()
                    }
                    else -> error = failure.message
                }
            } catch (_: IOException) {
                error = "Не удалось скачать файл. Проверьте интернет и свободное место."
            } catch (_: RuntimeException) {
                error = "Не удалось подготовить установку. Попробуйте ещё раз."
            } finally {
                progressReporting.cancel()
                downloadProgress = null
                if (!committed) {
                    prepared?.let(installer::abandon)
                    sessionId = null
                    phase = UpdatePhase.IDLE
                }
            }
        }
    }

    fun cancelDownload() {
        if (canCancelDownload) downloading?.cancel()
    }

    fun confirmationOpened() {
        confirmation = null
    }

    fun confirmationFailed() {
        sessionId?.let(installer::abandon)
        sessionId = null
        confirmation = null
        phase = UpdatePhase.IDLE
        error = "Не удалось открыть установщик Android. Попробуйте ещё раз."
    }
}
