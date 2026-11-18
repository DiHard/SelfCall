package ru.ifx.selfcall.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.ifx.selfcall.call.Screen
import ru.ifx.selfcall.data.CallContact

@Composable
internal fun CallDirectoryScreen(
    state: Screen.Calls,
    onSetup: () -> Unit,
    onDisable: () -> Unit,
    onDial: (CallContact) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    notificationsAllowed: Boolean,
    needsFullScreenPermission: Boolean,
    onFullScreenSettings: () -> Unit,
    onNotificationSettings: () -> Unit,
) {
    val directory = state.directory
    val contacts = directory?.contacts.orEmpty()
    val duplicateNames = contacts.groupingBy { it.name.lowercase() }.eachCount()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CallsHeader("Звонки коллегам", "На главную", onBack, enabled = !state.busy) {
            IconButton(
                onClick = onRefresh,
                enabled = !state.busy,
                colors = IconButtonDefaults.iconButtonColors(contentColor = LocalExtraColors.current.accentText),
            ) {
                Icon(Icons.Refresh, contentDescription = "Обновить список коллег")
            }
        }
        LazyColumn(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().weight(1f).then(screenEntrance()),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (directory == null && state.busy) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.height(20.dp).widthIn(max = 20.dp), strokeWidth = 2.dp)
                        Text("Загружаем список коллег…", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
            if (directory != null) {
                item {
                    IncomingCallStatus(
                        incoming = directory.incoming,
                        notificationsAllowed = notificationsAllowed,
                        enabled = !state.busy,
                        onSetup = onSetup,
                        onDisable = onDisable,
                        onNotificationSettings = onNotificationSettings,
                        onFullScreenSettings = onFullScreenSettings.takeIf { needsFullScreenPermission },
                    )
                }
            }
            state.error?.let { error ->
                item {
                    ErrorText(error, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            if (directory != null) {
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text("Коллеги", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                }
                if (contacts.isEmpty()) {
                    item {
                        Text("Здесь появятся коллеги, которые включат входящие звонки в SelfCall.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(contacts, key = { it.login }) { contact ->
                    val duplicate = (duplicateNames[contact.name.lowercase()] ?: 0) > 1
                    Surface(
                        modifier = Modifier.animateItem().fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            InitialsAvatar(contact.name, 40.dp, MaterialTheme.typography.titleMedium,
                                Modifier.clearAndSetSemantics { })
                            Column(Modifier.weight(1f)) {
                                Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                                if (duplicate) Text(contact.login, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton(
                                onClick = { onDial(contact) },
                                enabled = !state.busy,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.heightIn(min = 48.dp).semantics {
                                    contentDescription = "Позвонить: ${contact.name}" +
                                        if (duplicate) ", ${contact.login}" else ""
                                },
                            ) { Text("Позвонить", textAlign = TextAlign.Center) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallsHeader(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    enabled: Boolean,
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = enabled) {
            Icon(Icons.Back, contentDescription = backLabel)
        }
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        actions()
    }
}

@Composable
private fun IncomingCallStatus(
    incoming: CallContact?,
    notificationsAllowed: Boolean,
    enabled: Boolean,
    onSetup: () -> Unit,
    onDisable: () -> Unit,
    onNotificationSettings: () -> Unit,
    onFullScreenSettings: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val title = when {
        incoming == null -> "Входящие звонки выключены"
        notificationsAllowed -> "Входящие звонки включены"
        else -> "Уведомления о звонках выключены"
    }
    val description = when {
        incoming == null -> "Укажите имя, чтобы коллеги могли найти вас в списке и позвонить."
        notificationsAllowed -> "Коллеги могут позвонить вам, выбрав в списке имя «${incoming.name}»."
        else -> "В списке коллег вы видны как «${incoming.name}». Разрешите уведомления, чтобы принимать звонки."
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainer,
        contentColor = colors.onSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (incoming == null) {
                PrimaryAction("Настроить входящие звонки", onSetup, enabled = enabled)
            } else {
                val linkColors = ButtonDefaults.textButtonColors(contentColor = LocalExtraColors.current.accentText)
                if (!notificationsAllowed) {
                    PrimaryAction("Открыть настройки уведомлений", onNotificationSettings, enabled = enabled)
                } else if (onFullScreenSettings != null) {
                    TextButton(onClick = onFullScreenSettings, enabled = enabled, colors = linkColors,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Показывать звонки на заблокированном экране", textAlign = TextAlign.Center)
                    }
                }
                TextButton(onClick = onDisable, enabled = enabled, colors = linkColors,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Выключить входящие звонки", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
internal fun IncomingSetupScreen(
    state: Screen.IncomingSetup,
    notificationsAllowed: Boolean,
    onEditName: (String) -> Unit,
    onEnable: () -> Unit,
    onNotificationSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var nameError by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val openSettings = !notificationsAllowed && state.notificationsBlocked

    fun submit() {
        if (state.enabling) return
        nameError = state.name.isBlank()
        if (nameError) return
        focus.clearFocus()
        if (openSettings) onNotificationSettings() else onEnable()
    }

    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CallsHeader("Входящие звонки", "К списку коллег", onBack, enabled = !state.enabling)
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp).then(screenEntrance()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Укажите имя, под которым коллеги увидят вас в списке и смогут позвонить.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = {
                    nameError = false
                    onEditName(it)
                },
                label = { Text("Ваше имя") },
                supportingText = if (nameError) {
                    { ErrorText("Введите имя", Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                } else null,
                isError = nameError,
                singleLine = true,
                enabled = !state.enabling,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LocalExtraColors.current.accentText,
                    focusedLabelColor = LocalExtraColors.current.accentText,
                    errorLabelColor = LocalExtraColors.current.dangerText,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!notificationsAllowed) {
                val instructions = if (openSettings) {
                    "Включите уведомления SelfCall в настройках Android и вернитесь в приложение."
                } else {
                    "Если Android попросит разрешить уведомления, нажмите «Разрешить», чтобы получать звонки."
                }
                Text(instructions, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            state.error?.let { ErrorText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            val label = when {
                state.enabling -> "Включаем входящие звонки…"
                openSettings -> "Открыть настройки уведомлений"
                else -> "Включить входящие звонки"
            }
            PrimaryAction(label, ::submit, busy = state.enabling)
        }
    }
}

@Composable
internal fun DirectCallScreen(state: Screen.Direct, onAnswer: () -> Unit, onClose: () -> Unit) {
    val call = state.call
    val status = when (call.status) {
        "ringing" -> if (call.incoming) "Входящий звонок" else "Звоним…"
        "accepted" -> if (state.busy) "Подключаемся…" else "Звонок принят"
        "declined" -> "Звонок отклонён"
        "expired" -> if (call.incoming) "Пропущенный звонок" else "Нет ответа"
        "unreachable" -> "Не удалось доставить вызов"
        else -> "Звонок завершён"
    }
    val ringing = call.status == "ringing"
    val canAnswer = call.incoming && ringing
    val canHangUp = ringing || call.status == "accepted"
    DarkSystemBars()
    CallTheme {
        val colors = MaterialTheme.colorScheme
        val extra = LocalExtraColors.current
        CenteredPage {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                InitialsAvatar(call.name, 104.dp, MaterialTheme.typography.headlineLarge,
                    Modifier.clearAndSetSemantics { })
                Spacer(Modifier.height(20.dp))
                ScreenTitle(call.name, centered = true)
                Spacer(Modifier.height(6.dp))
                Crossfade(
                    status,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = status
                        liveRegion = LiveRegionMode.Polite
                    },
                    animationSpec = tween(StateMillis),
                    label = "Состояние звонка",
                ) { message ->
                    Text(message, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center)
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    ErrorText(it)
                }
            }
            Spacer(Modifier.height(36.dp))
            if (canAnswer) {
                PrimaryAction(
                    if (state.busy) "Подключаемся…" else "Ответить",
                    onClick = onAnswer,
                    busy = state.busy,
                    icon = Icons.Phone,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StageColors.accept,
                        contentColor = BrandForeground,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onClose,
                    enabled = !state.busy,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = extra.dangerText),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("Отклонить") }
            } else if (canHangUp) {
                PrimaryAction(
                    if (ringing) "Отменить звонок" else "Завершить звонок",
                    onClick = onClose,
                    enabled = !state.busy,
                    icon = Icons.HangUp,
                    colors = ButtonDefaults.buttonColors(containerColor = StageColors.hangUp, contentColor = BrandForeground),
                )
            } else {
                FilledTonalButton(
                    onClick = onClose,
                    enabled = !state.busy,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("К списку коллег") }
            }
        }
    }
}
