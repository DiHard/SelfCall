package ru.ifx.selfcall.ui

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberTrackMuted
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.compose.types.TrackReference
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.track.Track
import io.livekit.android.util.flow
import kotlinx.coroutines.delay
import java.util.Locale

/** Звонок: видео участников, внизу кнопки. Экран всегда тёмный, как видеоплеер. */
@Composable
fun CallScreen(
    meetingId: String,
    room: Room,
    startedAt: Long?,
    onMic: (Boolean) -> Unit,
    onCam: (Boolean) -> Unit,
    onSwitchCamera: () -> Unit,
    onLeave: () -> Unit,
    directName: String? = null,
) {
    DarkSystemBars()

    CallTheme {
        RoomScope(passedRoom = room, connect = false, disconnectOnDispose = false) {
            val tracks by rememberTracks(usePlaceholders = setOf(Track.Source.CAMERA))
            val local = room.localParticipant
            val micOn by local::isMicrophoneEnabled.flow.collectAsState()
            val camOn by local::isCameraEnabled.flow.collectAsState()
            val remotes by room::remoteParticipants.flow.collectAsState()
            val connection by room::state.flow.collectAsState()

            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .safeDrawingPadding(),
            ) {
                TopInfo(meetingId, remotes.size + 1, startedAt, directName, connection)
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
                    Stage(tracks, room)
                }
                Controls(
                    micOn = micOn,
                    camOn = camOn,
                    onMic = { onMic(!micOn) },
                    onCam = { onCam(!camOn) },
                    onSwitchCamera = onSwitchCamera,
                    onLeave = onLeave,
                    direct = directName != null,
                )
            }
        }
    }
}

@Composable
private fun TopInfo(meetingId: String, count: Int, startedAt: Long?, directName: String?, connection: Room.State) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val status = when (connection) {
        Room.State.CONNECTING -> "Подключаемся…"
        Room.State.CONNECTED -> "На связи"
        Room.State.RECONNECTING -> "Восстанавливаем связь…"
        Room.State.DISCONNECTED -> "Связь прервалась"
    }
    val dot by animateColorAsState(
        if (connection == Room.State.CONNECTED) StageColors.connected else StageColors.waiting,
        tween(StateMillis),
        label = "Цвет состояния связи",
    )
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                directName ?: meetingId,
                Modifier.semantics { heading() },
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = if (directName == null) FontFamily.Monospace else null,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Box(Modifier.size(7.dp).background(dot, CircleShape))
                Text(status, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (startedAt != null) CallDuration(startedAt, directName != null)
        }
        Row(
            Modifier.clearAndSetSemantics {
                contentDescription = "$count ${plural(count, "участник", "участника", "участников")}"
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Users, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text("$count", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (directName == null) {
            IconButton(onClick = { shareMeeting(context, meetingId) }) {
                Icon(Icons.Share, contentDescription = "Отправить ссылку на встречу", tint = colors.onSurface)
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun CallDuration(startedAt: Long, direct: Boolean) {
    val elapsedMs by produceState(SystemClock.elapsedRealtime() - startedAt, startedAt) {
        while (true) {
            value = SystemClock.elapsedRealtime() - startedAt
            delay(1000 - value % 1000)
        }
    }
    val seconds = elapsedMs / 1000
    val hours = seconds / 3600
    val minutes = seconds / 60 % 60
    val duration = if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds % 60)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds % 60)
    }
    Text(
        duration,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = if (direct) "Длительность звонка, $duration" else "Длительность встречи, $duration"
        },
    )
}

/**
 * Раскладка: если кто-то показывает экран — он крупно, остальные лентой снизу.
 * Иначе сетка: один — во весь экран, двое — друг под другом, больше — по два в ряд.
 */
@Composable
private fun Stage(tracks: List<TrackReference>, room: Room) {
    val screen = tracks.firstOrNull { it.source == Track.Source.SCREEN_SHARE && !it.isPlaceholder() }
    if (screen != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(screen, room, Modifier.weight(1f).fillMaxWidth(), fit = true)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(120.dp)) {
                items(tracks.filter { it != screen }, key = { key(it) }) {
                    Tile(it, room, Modifier.fillMaxHeight().aspectRatio(3f / 4f))
                }
            }
        }
        return
    }

    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = 8.dp
        when {
            tracks.size <= 1 -> tracks.firstOrNull()?.let { Tile(it, room, Modifier.fillMaxSize()) }
            tracks.size == 2 && !landscape -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                tracks.forEach { Tile(it, room, Modifier.weight(1f).fillMaxWidth()) }
            }
            else -> {
                val columns = if (landscape) minOf(tracks.size, 3) else 2
                val rows = (tracks.size + columns - 1) / columns
                // Плитки делят высоту поровну, но не меньше 160dp: дальше — прокрутка
                val tileHeight = maxOf((maxHeight - gap * (rows - 1)) / rows, 160.dp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    items(tracks, key = { key(it) }) { Tile(it, room, Modifier.fillMaxWidth().height(tileHeight)) }
                }
            }
        }
    }
}

private fun key(t: TrackReference) = "${t.participant.sid.value}:${t.source}"

@Composable
private fun Tile(track: TrackReference, room: Room, modifier: Modifier, fit: Boolean = false) {
    val participant = track.participant
    val name by participant::name.flow.collectAsState()
    val speaking by participant::isSpeaking.flow.collectAsState()
    val micOn by participant::isMicrophoneEnabled.flow.collectAsState()
    val muted by rememberTrackMuted(track)
    val isLocal = participant is LocalParticipant
    val shownName = when {
        track.source == Track.Source.SCREEN_SHARE -> "Экран: ${name ?: "Гость"}"
        isLocal -> "${name ?: "Гость"} (вы)"
        else -> name ?: "Гость"
    }
    val shape = MaterialTheme.shapes.large
    val frame by animateColorAsState(
        if (speaking) StageColors.accent else Color.Transparent,
        tween(StateMillis),
        label = "Говорит участник",
    )

    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(3.dp, frame, shape),
    ) {
        if (!track.isPlaceholder() && !muted) {
            VideoTrackView(
                trackReference = track,
                modifier = Modifier.fillMaxSize(),
                room = room,
                mirror = isLocal && track.source == Track.Source.CAMERA,
                scaleType = if (fit) ScaleType.FitInside else ScaleType.Fill,
            )
        } else {
            InitialsAvatar(name ?: "Гость", 72.dp, MaterialTheme.typography.headlineMedium,
                Modifier.align(Alignment.Center).clearAndSetSemantics { })
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(StageColors.scrim, MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!micOn && track.source == Track.Source.CAMERA) {
                Icon(
                    Icons.MicOff,
                    contentDescription = "Микрофон выключен",
                    tint = LocalExtraColors.current.dangerText,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                shownName,
                color = StageColors.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Controls(
    micOn: Boolean,
    camOn: Boolean,
    onMic: () -> Unit,
    onCam: () -> Unit,
    onSwitchCamera: () -> Unit,
    onLeave: () -> Unit,
    direct: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth().then(screenEntrance()),
            shape = MaterialTheme.shapes.large,
            color = colors.surface,
            contentColor = colors.onSurface,
        ) {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top,
            ) {
                val shape = MaterialTheme.shapes.medium
                CallControl("Микрофон", Modifier.weight(1f)) {
                    RoundToggle(micOn, Icons.Mic, Icons.MicOff, "Микрофон", onMic, shape = shape)
                }
                CallControl("Камера", Modifier.weight(1f)) {
                    RoundToggle(camOn, Icons.Cam, Icons.CamOff, "Камера", onCam, shape = shape)
                }
                CallControl("Сменить камеру", Modifier.weight(1f)) {
                    FilledIconButton(
                        onClick = onSwitchCamera,
                        enabled = camOn,
                        modifier = Modifier.size(48.dp),
                        shape = shape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = StageColors.controls,
                            contentColor = colors.onSurface,
                        ),
                    ) {
                        Icon(Icons.SwitchCam, contentDescription = "Сменить камеру")
                    }
                }
                CallControl("Выйти", Modifier.weight(1f)) {
                    FilledIconButton(
                        onClick = onLeave,
                        modifier = Modifier.size(width = 54.dp, height = 48.dp),
                        shape = shape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = StageColors.hangUp,
                            contentColor = BrandForeground,
                        ),
                    ) {
                        Icon(Icons.HangUp, contentDescription = if (direct) "Выйти из звонка" else "Выйти из встречи")
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControl(label: String, modifier: Modifier, button: @Composable () -> Unit) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        button()
        Text(label, Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}
