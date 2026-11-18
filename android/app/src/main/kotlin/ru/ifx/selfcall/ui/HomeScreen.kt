package ru.ifx.selfcall.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.ifx.selfcall.call.Screen
import ru.ifx.selfcall.data.MeetingLinks

@Composable
fun HomeScreen(
    state: Screen.Home,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onCalls: () -> Unit,
    updateOffer: (@Composable () -> Unit)? = null,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var codeError by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        val id = MeetingLinks.parse(code)
        if (id == null) {
            codeError = if (code.isBlank()) {
                "Вставьте ссылку на встречу или её код"
            } else {
                "Не похоже на ссылку встречи. Код выглядит так: abc-defg-hjk"
            }
        } else {
            onOpen(id)
        }
    }

    CenteredPage(top = { BrandBar() }) {
        Text(
            "Видеовстречи по ссылке",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Создайте встречу и отправьте ссылку. Собеседник сможет войти прямо из браузера.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Panel(Modifier.animateContentSize(tween(StateMillis))) {
            PrimaryAction(
                if (state.creating) "Создаём встречу…" else "Создать встречу",
                onClick = onCreate,
                busy = state.creating,
                icon = Icons.Plus,
            )
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                ErrorText(it)
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it
                    codeError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ссылка или код встречи") },
                placeholder = { Text("abc-defg-hjk") },
                supportingText = codeError?.let { message -> { ErrorText(message) } },
                singleLine = true,
                isError = codeError != null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = ::submit,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Войти") }
        }
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(
            onClick = onCalls,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Звонки коллегам", textAlign = TextAlign.Center) }
        if (updateOffer != null) {
            Spacer(Modifier.height(16.dp))
            updateOffer()
        }
    }
}
