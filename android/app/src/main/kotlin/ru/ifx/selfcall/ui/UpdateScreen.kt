package ru.ifx.selfcall.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.ifx.selfcall.update.AppUpdates
import ru.ifx.selfcall.update.DownloadProgress
import ru.ifx.selfcall.update.UpdatePhase
import java.util.Locale

@Composable
internal fun UpdateOffer(versionName: String, updates: AppUpdates, onUpdate: () -> Unit) {
    Panel(Modifier.then(screenEntrance())) {
        Text("Доступна версия $versionName", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        UpdateHelp(updates)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onUpdate, shape = MaterialTheme.shapes.small,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(actionLabel(updates))
            }
            TextButton(onClick = updates::dismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Позже")
            }
        }
    }
}

@Composable
internal fun UpdateScreen(updates: AppUpdates, onUpdate: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val phase = updates.phase
    val download = updates.downloadProgress
    CenteredPage(top = { BrandBar() }) {
        Panel(Modifier.animateContentSize(tween(StateMillis)), alignment = Alignment.CenterHorizontally) {
            StatusMark(Icons.Download, colors.surfaceContainer, LocalExtraColors.current.accentText)
            Spacer(Modifier.height(16.dp))
            ScreenTitle(if (updates.required) "Обновите SelfCall" else "Обновление SelfCall", centered = true)
            Spacer(Modifier.height(8.dp))
            val message = when (phase) {
                UpdatePhase.IDLE -> "Чтобы продолжить, установите версию ${updates.available?.versionName}."
                UpdatePhase.DOWNLOADING -> if (download == null) "Подключаемся к серверу…" else "Скачиваем обновление…"
                UpdatePhase.VERIFYING -> "Проверяем файл обновления…"
                UpdatePhase.INSTALLING -> "Подтвердите установку в Android."
            }
            Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            UpdateHelp(updates)
            if (phase == UpdatePhase.IDLE) {
                Spacer(Modifier.height(20.dp))
                PrimaryAction(actionLabel(updates), onUpdate)
            } else if (updates.canCancelDownload) {
                Spacer(Modifier.height(20.dp))
                if (download == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else DownloadStatus(download)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = updates::cancelDownload, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (phase == UpdatePhase.DOWNLOADING) "Отменить загрузку" else "Отменить обновление")
                }
            }
        }
    }
}

@Composable
private fun DownloadStatus(download: DownloadProgress) {
    val percent = download.downloadedBytes * 100 / download.totalBytes
    val fraction = download.downloadedBytes.toFloat() / download.totalBytes
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Загрузка обновления" },
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "$percent\u00a0%",
        modifier = Modifier.clearAndSetSemantics { },
        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
    )
    Text(
        String.format(Locale.forLanguageTag("ru"), "%.1f из %.1f\u00a0МБ",
            download.downloadedBytes / 1_000_000.0, download.totalBytes / 1_000_000.0),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun UpdateHelp(updates: AppUpdates) {
    val message = updates.error ?: if (updates.needsInstallPermission) {
        "Разрешите SelfCall устанавливать обновления в настройках Android."
    } else return
    Spacer(Modifier.height(8.dp))
    val announce = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    if (updates.error != null) ErrorText(message, announce)
    else Text(message, modifier = announce, style = MaterialTheme.typography.bodyMedium)
}

private fun actionLabel(updates: AppUpdates) = when {
    updates.needsInstallPermission -> "Открыть настройки"
    updates.error != null -> "Повторить"
    else -> "Обновить"
}
