package ru.ifx.selfcall.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.ifx.selfcall.data.Api
import ru.ifx.selfcall.data.Room
import ru.ifx.selfcall.data.UsernameStore

private sealed interface RoomsState {
    data object Loading : RoomsState
    data class Loaded(val rooms: List<Room>) : RoomsState
    data class Error(val message: String) : RoomsState
}

@Composable
fun RoomsScreen(
    onJoin: (String) -> Unit,
    onChangeName: () -> Unit,
) {
    val ctx = LocalContext.current
    val username = remember { UsernameStore(ctx).username }
    var state by remember { mutableStateOf<RoomsState>(RoomsState.Loading) }

    LaunchedEffect(Unit) {
        state = try {
            RoomsState.Loaded(Api.fetchRooms())
        } catch (t: Throwable) {
            RoomsState.Error(t.message ?: t.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Комнаты", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(username, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onChangeName) { Text("сменить") }
            }
        }

        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            RoomsState.Loading -> Text("Загрузка…")
            is RoomsState.Error -> Text("Ошибка: ${s.message}", color = MaterialTheme.colorScheme.error)
            is RoomsState.Loaded -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.rooms) { room ->
                    OutlinedButton(
                        onClick = { onJoin(room.name) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            room.display.ifBlank { room.name },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
