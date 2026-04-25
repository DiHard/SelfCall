package ru.ifx.selfcall.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.track.Track
import kotlinx.coroutines.launch
import ru.ifx.selfcall.data.Api
import ru.ifx.selfcall.data.TokenResponse
import ru.ifx.selfcall.data.UsernameStore

private val ROOM_DISPLAY_NAMES = mapOf(
    "room-1" to "Комната «Млечный путь»",
    "room-2" to "Комната «Андромеда»",
    "room-3" to "Комната «Треугольник»",
    "room-4" to "Комната «Центавр»",
    "room-5" to "Комната «Водоворот»",
)

private sealed interface CallState {
    data object Permissions : CallState
    data object Loading : CallState
    data class Ready(val token: TokenResponse) : CallState
    data class Error(val message: String) : CallState
}

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
)

@Composable
fun CallScreen(roomName: String, onLeave: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val username = remember { UsernameStore(ctx).username }
    val displayName = remember { ROOM_DISPLAY_NAMES[roomName] ?: roomName }

    val hasPermissions = remember {
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    var state by remember {
        mutableStateOf<CallState>(
            if (hasPermissions) CallState.Loading else CallState.Permissions
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            state = CallState.Loading
        } else {
            state = CallState.Error("Нужны разрешения на камеру и микрофон")
        }
    }

    LaunchedEffect(state) {
        if (state is CallState.Loading) {
            scope.launch {
                state = try {
                    CallState.Ready(Api.fetchToken(roomName, username))
                } catch (t: Throwable) {
                    CallState.Error(t.message ?: t.toString())
                }
            }
        }
    }

    when (val s = state) {
        CallState.Permissions -> PermissionsScreen(
            onRequest = { permLauncher.launch(REQUIRED_PERMISSIONS) },
            onCancel = onLeave,
        )
        CallState.Loading -> CenterMessage("Подключение к «$displayName»…")
        is CallState.Error -> ErrorScreen(message = s.message, onBack = onLeave)
        is CallState.Ready -> ActiveCall(token = s.token, onLeave = onLeave)
    }
}

@Composable
private fun PermissionsScreen(onRequest: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Для звонка нужны доступ к камере и микрофону")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Разрешить") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Отмена") }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}

@Composable
private fun ErrorScreen(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Ошибка: $message", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Назад") }
    }
}

@Composable
private fun ActiveCall(token: TokenResponse, onLeave: () -> Unit) {
    RoomScope(
        url = token.url,
        token = token.token,
        audio = true,
        video = true,
        connect = true,
        onDisconnected = { onLeave() },
    ) {
        val tracks by rememberTracks(
            sources = listOf(Track.Source.CAMERA),
            usePlaceholders = setOf(),
        )

        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (tracks.size > 1) 2 else 1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(tracks) { track ->
                    VideoTrackView(
                        trackReference = track,
                        modifier = Modifier.aspectRatio(3f / 4f),
                    )
                }
            }

            Button(
                onClick = onLeave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
            ) {
                Text("Завершить звонок")
            }
        }
    }
}
