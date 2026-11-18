package ru.ifx.selfcall.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.flow.MutableStateFlow
import ru.ifx.selfcall.data.AndroidRelease
import ru.ifx.selfcall.data.ApiException
import java.io.File

internal class UpdateInstaller(private val context: Context) {
    private val pm = context.packageManager
    private val installer get() = pm.packageInstaller

    @Suppress("DEPRECATION") // API 26–27 still use GET_SIGNATURES.
    fun prepare(apk: File, release: AndroidRelease): Int {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(context.packageName, flags)
        val candidate = pm.getPackageArchiveInfo(apk.path, flags)
        val installedSigners = signers(installed)
        if (candidate == null || candidate.packageName != installed.packageName ||
            PackageInfoCompat.getLongVersionCode(candidate) != release.versionCode.toLong() ||
            PackageInfoCompat.getLongVersionCode(candidate) <= PackageInfoCompat.getLongVersionCode(installed) ||
            candidate.versionName != release.versionName || installedSigners.isEmpty() || signers(candidate) != installedSigners
        ) {
            throw ApiException("invalid_apk", "Файл обновления не прошёл проверку. Скачайте его ещё раз.")
        }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(release.sizeBytes)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, release.sizeBytes).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
            }
            return id
        } catch (error: Exception) {
            abandon(id)
            throw error
        }
    }

    fun commit(id: Int) {
        val intent = Intent(context, UpdateInstallReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val result = PendingIntent.getBroadcast(context, id, intent, flags)
        installer.openSession(id).use { it.commit(result.intentSender) }
    }

    fun abandon(id: Int) {
        runCatching { installer.abandonSession(id) }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<Signature> =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
            ?.toSet().orEmpty()
}

internal data class InstallResult(val sessionId: Int, val status: Int, val confirmation: Intent?)

class UpdateInstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val confirmation = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        results.value = InstallResult(
            intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1),
            intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
            confirmation,
        )
    }

    internal companion object {
        val results = MutableStateFlow<InstallResult?>(null)
    }
}
