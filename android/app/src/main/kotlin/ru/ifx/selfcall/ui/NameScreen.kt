package ru.ifx.selfcall.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.ifx.selfcall.data.UsernameStore

@Composable
fun NameScreen(onContinue: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { UsernameStore(ctx) }
    var name by remember { mutableStateOf(store.username) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "SelfCall",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Введите имя, под которым вас будут видеть",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(64) },
            label = { Text("Ваше имя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val trimmed = name.trim()
                if (trimmed.isNotEmpty()) {
                    store.username = trimmed
                    onContinue()
                }
            },
            enabled = name.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Продолжить")
        }
    }
}
