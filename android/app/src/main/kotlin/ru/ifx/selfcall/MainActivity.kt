package ru.ifx.selfcall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import ru.ifx.selfcall.ui.AppNav

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFC084FC),
                    background = Color(0xFF16171D),
                    surface = Color(0xFF1F2028),
                ),
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNav()
                }
            }
        }
    }
}
