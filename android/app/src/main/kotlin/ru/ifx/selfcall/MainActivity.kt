package ru.ifx.selfcall

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.livekit.android.room.Room
import io.livekit.android.util.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import ru.ifx.selfcall.call.IncomingCalls
import ru.ifx.selfcall.call.MeetingViewModel
import ru.ifx.selfcall.call.Screen
import ru.ifx.selfcall.data.MeetingLinks
import ru.ifx.selfcall.ui.CallScreen
import ru.ifx.selfcall.ui.CallDirectoryScreen
import ru.ifx.selfcall.ui.DirectCallScreen
import ru.ifx.selfcall.ui.CheckingScreen
import ru.ifx.selfcall.ui.FailedScreen
import ru.ifx.selfcall.ui.HomeScreen
import ru.ifx.selfcall.ui.IncomingSetupScreen
import ru.ifx.selfcall.ui.LeftScreen
import ru.ifx.selfcall.ui.PreJoinScreen
import ru.ifx.selfcall.ui.SelfCallTheme
import ru.ifx.selfcall.ui.SignInScreen
import ru.ifx.selfcall.ui.UpdateOffer
import ru.ifx.selfcall.ui.UpdateScreen

class MainActivity : ComponentActivity() {
    private val vm: MeetingViewModel by viewModels()
    private var afterPermission: ((Boolean) -> Unit)? = null
    private var fullScreenAllowed by mutableStateOf(true)
    private var callNotificationsAllowed by mutableStateOf(true)
    private var explicitIncoming: String? = null
    private val requestPermission = activityResultRegistry.register(
        "call-permission", this, ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val resume = afterPermission
        afterPermission = null
        resume?.invoke(granted)
    }
    private val requestCallNotifications = activityResultRegistry.register(
        "incoming-notification-permission", this, ActivityResultContracts.RequestPermission(),
    ) { completeIncomingPermission() }
    private val notificationSettings = activityResultRegistry.register(
        "incoming-notification-settings", this, ActivityResultContracts.StartActivityForResult(),
    ) { completeIncomingPermission() }
    private val installPermission = activityResultRegistry.register(
        "install-permission", this, ActivityResultContracts.StartActivityForResult(),
    ) {
        if (packageManager.canRequestPackageInstalls() && !vm.keepCallScreen) vm.updates.startDownload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Приложение могло быть запущено ссылкой. При восстановлении окна (из «Недавних») её не открываем повторно.
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            handleIntent(intent)
        }

        // Свернули приложение посреди звонка — выключаем камеру, вернулись — включаем
        val foregroundObserver = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = vm.onAppBackground()
            override fun onStart(owner: LifecycleOwner) = vm.onAppForeground()
        }
        val processLifecycle = ProcessLifecycleOwner.get().lifecycle
        processLifecycle.addObserver(foregroundObserver)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = processLifecycle.removeObserver(foregroundObserver)
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    IncomingCalls.incoming.collect { id ->
                        if (id != null && id != explicitIncoming) vm.showIncoming(id)
                    }
                }
                launch {
                    while (true) {
                        vm.updates.refresh()
                        delay(60_000)
                    }
                }
                launch {
                    snapshotFlow { vm.updates.confirmation.takeUnless { vm.keepCallScreen } }
                        .filterNotNull().collect { confirmation ->
                            vm.updates.confirmationOpened()
                            try {
                                startActivity(confirmation)
                            } catch (_: ActivityNotFoundException) {
                                vm.updates.confirmationFailed()
                            } catch (_: SecurityException) {
                                vm.updates.confirmationFailed()
                            }
                        }
                }
            }
        }

        setContent {
            SelfCallTheme {
                val screen = vm.screen
                val updates = vm.updates
                val showUpdate = updates.blocksCalls && !vm.keepCallScreen && screen !is Screen.SignIn
                val incoming = when (screen) {
                    is Screen.Direct -> screen.call.incoming && (screen.call.status == "ringing" || screen.busy)
                    is Screen.InCall -> screen.direct?.incoming == true
                    else -> vm.openingIncoming
                }
                SideEffect { showOverLockScreen(incoming && !showUpdate) }

                if (showUpdate) {
                    UpdateScreen(updates, ::requestAppUpdate)
                    BackHandler(enabled = updates.canCancelDownload, onBack = updates::cancelDownload)
                    return@SelfCallTheme
                }

                // Экран во время звонка — см. KeepScreenDuringCall
                (screen as? Screen.InCall)?.let { KeepScreenDuringCall(it.room, window) }

                when (screen) {
                    is Screen.Home -> {
                        val offer = updates.offer
                        HomeScreen(screen, onCreate = vm::createMeeting, onOpen = { vm.open(it) },
                            onCalls = vm::openDirectory, updateOffer = if (offer != null) {
                                { UpdateOffer(offer.versionName, updates, ::requestAppUpdate) }
                            } else null)
                    }
                    is Screen.SignIn -> SignInScreen(screen, onSignIn = vm::signIn)
                    is Screen.Checking -> CheckingScreen(onBack = vm::goHome)
                    is Screen.PreJoin -> PreJoinScreen(
                        state = screen,
                        initialName = vm.prefs.initialMeetingName,
                        initialRememberName = vm.prefs.rememberedMeetingName != null,
                        initialMic = vm.prefs.micOn,
                        initialCam = vm.prefs.camOn,
                        onBack = vm::goHome,
                        onJoin = { name, rememberName, mic, cam ->
                            vm.prefs.micOn = mic
                            vm.prefs.camOn = cam
                            vm.join(name, rememberName, mic, cam)
                        },
                    )
                    is Screen.InCall -> CallScreen(
                        meetingId = screen.id,
                        room = screen.room,
                        startedAt = screen.startedAt,
                        onMic = { on ->
                            if (on && screen.direct != null) withPermission(Manifest.permission.RECORD_AUDIO) {
                                if (it) vm.setMic(true)
                            } else vm.setMic(on)
                        },
                        onCam = { on ->
                            if (on && screen.direct != null) withPermission(Manifest.permission.CAMERA) {
                                if (it) vm.setCam(true)
                            } else vm.setCam(on)
                        },
                        onSwitchCamera = vm::switchCamera,
                        onLeave = vm::leave,
                        directName = screen.direct?.name,
                    )
                    is Screen.Calls -> CallDirectoryScreen(
                        state = screen, onSetup = vm::startIncomingSetup, onDisable = vm::disableIncoming,
                        onDial = { contact -> withPermission(Manifest.permission.RECORD_AUDIO) { vm.dial(contact) } },
                        onRefresh = vm::openDirectory, onBack = vm::goHome,
                        notificationsAllowed = callNotificationsAllowed,
                        needsFullScreenPermission = !fullScreenAllowed,
                        onFullScreenSettings = ::openFullScreenSettings,
                        onNotificationSettings = ::openNotificationSettings,
                    )
                    is Screen.IncomingSetup -> IncomingSetupScreen(
                        state = screen,
                        notificationsAllowed = callNotificationsAllowed,
                        onEditName = vm::editIncomingName,
                        onEnable = ::enableIncoming,
                        onNotificationSettings = ::openNotificationSettings,
                        onBack = vm::closeIncomingSetup,
                    )
                    is Screen.Direct -> DirectCallScreen(screen,
                        onAnswer = { withPermission(Manifest.permission.RECORD_AUDIO) { vm.answerDirect() } },
                        onClose = vm::closeDirect)
                    is Screen.Left -> LeftScreen(screen, onRejoin = vm::retry, onHome = vm::goHome)
                    is Screen.Failed -> FailedScreen(screen, onRetry = vm::retry, onHome = vm::goHome)
                }

                // «Назад» во время звонка сворачивает приложение, звонок продолжается.
                // На остальных экранах встречи — на главную.
                BackHandler(enabled = screen !is Screen.Home && screen !is Screen.SignIn) {
                    when (screen) {
                        is Screen.InCall -> moveTaskToBack(true)
                        is Screen.Direct -> vm.closeDirect()
                        is Screen.Calls -> if (!screen.busy) vm.goHome()
                        is Screen.IncomingSetup -> vm.closeIncomingSetup()
                        else -> vm.goHome()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        fullScreenAllowed = Build.VERSION.SDK_INT < 34 ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        updateIncomingNotificationAvailability()
    }

    private fun requestAppUpdate() {
        if (vm.keepCallScreen) return
        if (packageManager.canRequestPackageInstalls()) {
            vm.updates.startDownload()
            return
        }
        vm.updates.askInstallPermission()
        try {
            installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri()))
        } catch (_: ActivityNotFoundException) {
            vm.updates.installSettingsUnavailable()
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == IncomingCalls.OPEN || intent.action == IncomingCalls.ACCEPT) {
            val id = IncomingCalls.parseId(intent.getStringExtra(IncomingCalls.EXTRA_ID)) ?: return
            if (intent.getStringExtra(IncomingCalls.EXTRA_PROOF) != vm.prefs.callNotificationProof) return
            explicitIncoming = id
            if (vm.inCall) return
            if (vm.updates.blocksCalls) {
                IncomingCalls.dismiss(this, id)
                return
            }
            showOverLockScreen(true)
            if (intent.action == IncomingCalls.ACCEPT) {
                withPermission(Manifest.permission.RECORD_AUDIO) { vm.showIncoming(id, accept = true) }
            } else vm.showIncoming(id)
        } else openLink(intent)
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen(show: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(show)
            setTurnScreenOn(show)
        } else {
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (show) window.addFlags(flags) else window.clearFlags(flags)
        }
    }

    private fun enableIncoming() {
        IncomingCalls.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCallNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else completeIncomingPermission()
    }

    private fun completeIncomingPermission() {
        updateIncomingNotificationAvailability()
        if (callNotificationsAllowed) vm.enableIncoming() else vm.incomingPermissionDenied()
    }

    private fun updateIncomingNotificationAvailability() {
        val manager = getSystemService(NotificationManager::class.java)
        callNotificationsAllowed = manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(IncomingCalls.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun withPermission(permission: String, action: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) action(true)
        else if (afterPermission == null) {
            afterPermission = action
            requestPermission.launch(permission)
        }
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:$packageName")))
    }

    private fun openNotificationSettings() {
        val manager = getSystemService(NotificationManager::class.java)
        val intent = if (manager.areNotificationsEnabled() && manager.getNotificationChannel(IncomingCalls.CHANNEL) != null) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, IncomingCalls.CHANNEL)
        } else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        notificationSettings.launch(intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    /** Открыли ссылку на встречу (из мессенджера, браузера): сразу к экрану перед входом. */
    private fun openLink(intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        val id = MeetingLinks.parse(uri.toString()) ?: return
        if (vm.callInProgress) {
            Toast.makeText(this, "Сначала завершите текущий звонок или встречу", Toast.LENGTH_LONG).show()
        } else {
            vm.open(id)
        }
    }
}

/**
 * Экран во время звонка:
 * — камера включена — экран не гаснет совсем (пользователь смотрит видео);
 * — камера выключена — режим «как в звонке»: держим wake lock датчика приближения,
 *   и система сама гасит экран у уха и включает при отдалении. Если устройство
 *   такой wake lock не поддерживает — экран просто не гаснет, как раньше.
 */
@Composable
private fun KeepScreenDuringCall(room: Room, window: Window) {
    val camOn by room.localParticipant::isCameraEnabled.flow.collectAsState()
    DisposableEffect(camOn) {
        val proximity = if (!camOn) acquireProximityLock(window.context) else null
        if (proximity != null) {
            // Разговор без камеры: гасить экран должен датчик, FLAG_KEEP_SCREEN_ON только мешал бы
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            runCatching { proximity?.release() }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** Wake lock датчика приближения (экран гаснет у уха) или null, если устройство его не поддерживает. */
@android.annotation.SuppressLint("WakelockTimeout") // держим весь звонок и отпускаем сами; при краше процесса система сбросит wake lock
private fun acquireProximityLock(context: Context): PowerManager.WakeLock? = runCatching {
    val pm = context.getSystemService(PowerManager::class.java) ?: return@runCatching null
    if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return@runCatching null
    pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "selfcall:call").apply {
        setReferenceCounted(false)
        acquire()
    }
}.getOrNull()
