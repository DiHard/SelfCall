package ru.ifx.selfcall.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.ifx.selfcall.call.Screen

@Composable
fun SignInScreen(state: Screen.SignIn, onSignIn: (String, String) -> Unit) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showRequired by rememberSaveable { mutableStateOf(false) }
    val loginFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    fun submit() {
        if (state.submitting) return
        showRequired = true
        when {
            login.isBlank() -> loginFocus.requestFocus()
            password.isEmpty() -> passwordFocus.requestFocus()
            else -> onSignIn(login.trim(), password)
        }
    }

    CenteredPage(top = { BrandBar() }) {
        Panel {
            ScreenTitle("Вход в SelfCall")
            Spacer(Modifier.height(8.dp))
            Text(
                "Введите логин и пароль для доступа к SelfCall. Вход сохранится на этом телефоне.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            val loginMissing = showRequired && login.isBlank()
            OutlinedTextField(
                value = login,
                onValueChange = { login = it },
                modifier = Modifier.fillMaxWidth().focusRequester(loginFocus),
                label = { Text("Логин") },
                supportingText = if (loginMissing) { { ErrorText("Введите логин") } } else null,
                isError = loginMissing,
                enabled = !state.submitting,
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
            val passwordMissing = showRequired && password.isEmpty()
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
                label = { Text("Пароль") },
                supportingText = if (passwordMissing) { { ErrorText("Введите пароль") } } else null,
                isError = passwordMissing,
                enabled = !state.submitting,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                ErrorText(it)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryAction(if (state.submitting) "Входим…" else "Войти", ::submit, busy = state.submitting)
        }
    }
}
