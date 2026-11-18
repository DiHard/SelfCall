package ru.ifx.selfcall.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.livekit.android.compose.ui.CameraPreview
import io.livekit.android.room.track.CameraPosition
import kotlinx.coroutines.delay
import ru.ifx.selfcall.call.LeaveReason
import ru.ifx.selfcall.call.Screen
import ru.ifx.selfcall.data.Prefs

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Back, contentDescription = "На главную")
        }
        Logo()
    }
}

@Composable
fun CheckingScreen(onBack: () -> Unit) {
    CenteredPage(top = { TopBar(onBack) }) {
        Panel(alignment = Alignment.CenterHorizontally) {
            StatusMark(Icons.Link, MaterialTheme.colorScheme.surfaceContainer, LocalExtraColors.current.accentText)
            Spacer(Modifier.height(16.dp))
            ScreenTitle("Проверяем ссылку…", centered = true)
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.7f))
        }
    }
}

// ----- Экран перед входом -----

private fun Context.has(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
fun PreJoinScreen(
    state: Screen.PreJoin,
    initialName: String,
    initialRememberName: Boolean,
    initialMic: Boolean,
    initialCam: Boolean,
    onBack: () -> Unit,
    onJoin: (name: String, rememberName: Boolean, micOn: Boolean, camOn: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val info = state.info
    var name by rememberSaveable { mutableStateOf(initialName) }
    var rememberName by rememberSaveable { mutableStateOf(initialRememberName) }
    var micOn by rememberSaveable { mutableStateOf(initialMic) }
    var camOn by rememberSaveable { mutableStateOf(initialCam) }
    var micGranted by remember { mutableStateOf(context.has(Manifest.permission.RECORD_AUDIO)) }
    var camGranted by remember { mutableStateOf(context.has(Manifest.permission.CAMERA)) }
    // Отказали насовсем: система больше не покажет запрос, остаётся только экран настроек
    var micBlocked by remember { mutableStateOf(false) }
    var camBlocked by remember { mutableStateOf(false) }

    val activity = context as? Activity
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        micGranted = context.has(Manifest.permission.RECORD_AUDIO)
        camGranted = context.has(Manifest.permission.CAMERA)
        if (Manifest.permission.RECORD_AUDIO in res && !micGranted) {
            micBlocked = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: false
        }
        if (Manifest.permission.CAMERA in res && !camGranted) {
            camBlocked = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
        }
    }

    // Сразу спрашиваем всё, что нужно для звонка, одним окном
    LaunchedEffect(Unit) {
        val wanted = buildList {
            if (micOn && !micGranted) add(Manifest.permission.RECORD_AUDIO)
            if (camOn && !camGranted) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33 && !context.has(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (wanted.isNotEmpty()) askPermissions.launch(wanted.toTypedArray())
    }

    // Что человек получит на самом деле: без разрешения устройство считается выключенным
    val micLive = micOn && micGranted
    val camLive = camOn && camGranted

    fun toggleMic() {
        if (micGranted) micOn = !micOn
        else if (micBlocked) context.openAppSettings()
        else { micOn = true; askPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
    }
    fun toggleCam() {
        if (camGranted) camOn = !camOn
        else if (camBlocked) context.openAppSettings()
        else { camOn = true; askPermissions.launch(arrayOf(Manifest.permission.CAMERA)) }
    }

    // Возвращаемся из настроек телефона — перепроверяем разрешения
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        micGranted = context.has(Manifest.permission.RECORD_AUDIO)
        camGranted = context.has(Manifest.permission.CAMERA)
        if (micGranted) micBlocked = false
        if (camGranted) camBlocked = false
        onPauseOrDispose { }
    }

    val join = { if (!state.joining) onJoin(name, rememberName, micLive, camLive) }

    CenteredPage(top = { TopBar(onBack) }, centered = false) {
        ScreenTitle(if (state.justCreated) "Встреча создана" else "Вход во встречу")
        Spacer(Modifier.height(6.dp))
        val others = info.participants
        Text(
            when {
                state.justCreated -> "Отправьте ссылку тем, с кем хотите поговорить."
                others == 0 -> "Во встрече пока никого нет"
                else -> "Во встрече $others ${plural(others, "человек", "человека", "человек")}"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        LinkBox(info.id, state.justCreated)

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 10f)
                .clip(MaterialTheme.shapes.large).background(StageColors.background),
            contentAlignment = Alignment.Center,
        ) {
            // Камеру отпускаем до входа: звонку она нужна сама.
            if (camLive && !state.joining) {
                CameraPreview(CameraPosition.FRONT, Modifier.fillMaxSize(), mirror = true)
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 62.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.CamOff, contentDescription = null, tint = StageColors.secondaryText)
                    Text(
                        when {
                            state.joining -> "Подключаемся…"
                            camOn && !camGranted -> "Нет доступа к камере"
                            else -> "Камера выключена"
                        },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = StageColors.secondaryText,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoundToggle(micLive, Icons.Mic, Icons.MicOff, "Микрофон", ::toggleMic, enabled = !state.joining)
                RoundToggle(camLive, Icons.Cam, Icons.CamOff, "Камера", ::toggleCam, enabled = !state.joining)
            }
        }
        if (micOn && !micGranted) {
            PermissionHint("Нет доступа к микрофону. Вас не будет слышно.", micBlocked) { context.openAppSettings() }
        }
        if (camOn && !camGranted) {
            PermissionHint("Вы войдёте без видео.", camBlocked) { context.openAppSettings() }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = Prefs.limitMeetingName(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ваше имя — необязательно") },
            placeholder = { Text("Гость") },
            enabled = !state.joining,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { join() }),
        )
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = rememberName,
                enabled = !state.joining,
                role = Role.Checkbox,
                onValueChange = { rememberName = it },
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = rememberName, onCheckedChange = null, enabled = !state.joining)
            Column {
                Text("Запомнить меня", style = MaterialTheme.typography.bodyLarge)
                Text("Сохранить имя для следующих встреч на этом телефоне",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryAction(if (state.joining) "Входим…" else "Войти во встречу", join, busy = state.joining)
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            ErrorText(it)
        }
    }
}

@Composable
private fun PermissionHint(text: String, blocked: Boolean, openSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ErrorText(text, Modifier.weight(1f))
        if (blocked) TextButton(onClick = openSettings) { Text("Настройки") }
    }
}

@Composable
fun RoundToggle(
    on: Boolean,
    onIcon: ImageVector,
    offIcon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
) {
    val background by animateColorAsState(
        if (on) StageColors.overVideo else StageColors.mutedControl,
        tween(StateMillis),
        label = "Цвет кнопки устройства",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(PressMillis), label = "Нажатие")
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.semantics {
            contentDescription = label
            role = Role.Switch
            stateDescription = if (on) "Включено" else "Выключено"
        },
        shape = shape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = background,
            contentColor = StageColors.text,
            disabledContainerColor = StageColors.surface,
            disabledContentColor = StageColors.secondaryText,
        ),
    ) {
        Crossfade(on, animationSpec = tween(StateMillis), label = "Значок устройства") { active ->
            Icon(if (active) onIcon else offIcon, contentDescription = null)
        }
    }
}

/** Код встречи и кнопки «Поделиться» и «Копировать». */
@Composable
fun LinkBox(id: String, justCreated: Boolean) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val additional = LocalExtraColors.current
    var copies by remember(id) { mutableStateOf(0) }
    var settled by remember(id, justCreated) { mutableStateOf(!justCreated) }
    LaunchedEffect(id, justCreated) { settled = true }
    LaunchedEffect(id, copies) {
        if (copies > 0) {
            delay(2300)
            copies = 0
        }
    }
    val copied = copies > 0
    val background by animateColorAsState(
        when {
            copied -> additional.successSurface
            settled -> colors.surfaceContainer
            else -> colors.surfaceContainerHigh
        },
        tween(InviteMillis, easing = EnterEasing),
        label = "Ссылка на встречу",
    )
    Row(
        Modifier.fillMaxWidth().background(background, MaterialTheme.shapes.medium)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (copied) "Ссылка скопирована" else "Ссылка на встречу",
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = if (copied) additional.successText else colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(id, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(
            onClick = {
                copyMeetingLink(context, id)
                copies++
            },
            modifier = Modifier.semantics { contentDescription = "Скопировать ссылку" },
        ) {
            Crossfade(copied, animationSpec = tween(StateMillis), label = "Копирование ссылки") { done ->
                Icon(if (done) Icons.Check else Icons.Copy, contentDescription = null,
                    tint = if (done) additional.successText else additional.accentText)
            }
        }
        IconButton(onClick = { shareMeeting(context, id) }) {
            Icon(Icons.Share, contentDescription = "Отправить ссылку", tint = additional.accentText)
        }
    }
}

// ----- Сообщения: вышли, не нашли, нет связи -----

private val LEFT_TEXT = mapOf(
    LeaveReason.LEFT to ("Вы вышли из встречи" to "Ссылка продолжает работать — можно вернуться в любой момент."),
    LeaveReason.ENDED to (
        "Встреча завершена" to
            "Встреча шла дольше разрешённого времени. Чтобы продолжить, войдите снова по той же ссылке."
        ),
    LeaveReason.LOST to ("Связь прервалась" to "Проверьте интернет и вернитесь во встречу."),
)

@Composable
fun LeftScreen(state: Screen.Left, onRejoin: () -> Unit, onHome: () -> Unit) {
    val (title, text) = LEFT_TEXT.getValue(state.reason)
    MessageScreen(title, text, "Вернуться во встречу", state.reason == LeaveReason.LOST, onRejoin, onHome)
}

@Composable
fun FailedScreen(state: Screen.Failed, onRetry: () -> Unit, onHome: () -> Unit) {
    MessageScreen(state.title, state.text, if (state.retry) "Попробовать снова" else null, true, onRetry, onHome)
}

@Composable
private fun MessageScreen(
    title: String,
    text: String,
    primary: String?,
    error: Boolean,
    onPrimary: () -> Unit,
    onHome: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current
    CenteredPage(top = { BrandBar() }) {
        Panel(alignment = Alignment.CenterHorizontally) {
            if (error) StatusMark(Icons.Alert, colors.errorContainer, extra.dangerText)
            else StatusMark(Icons.Check, extra.successSurface, extra.successText)
            Spacer(Modifier.height(16.dp))
            ScreenTitle(title, centered = true)
            Spacer(Modifier.height(8.dp))
            Text(text, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            if (primary == null) {
                PrimaryAction("На главную", onHome)
            } else {
                PrimaryAction(primary, onPrimary)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onHome,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("На главную") }
            }
        }
    }
}
