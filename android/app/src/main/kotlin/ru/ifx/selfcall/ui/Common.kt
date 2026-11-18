package ru.ifx.selfcall.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import ru.ifx.selfcall.data.MeetingLinks

/** Знак сервиса: синий квадрат с камерой и название, как на сайте. */
@Composable
fun Logo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .background(BrandBackground, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Cam, contentDescription = null, tint = BrandForeground, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("SelfCall", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

internal const val PressMillis = 120
internal const val StateMillis = 160
internal const val PanelMillis = 240
internal const val InviteMillis = 340
internal val EnterEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Вход нового содержимого без задержки его действий и жизненного цикла. */
@Composable
internal fun screenEntrance(): Modifier {
    val progress = remember { Animatable(0f) }
    val distance = with(LocalDensity.current) { 6.dp.toPx() }
    LaunchedEffect(progress) { progress.animateTo(1f, tween(PanelMillis, easing = EnterEasing)) }
    return Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * distance
    }
}

@Composable
internal fun BrandBar() {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Logo()
    }
}

/** Страница с содержимым по центру: не шире 480dp, прокручивается, не прячется под клавиатурой. */
@Composable
fun CenteredPage(
    modifier: Modifier = Modifier,
    top: (@Composable () -> Unit)? = null,
    centered: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        top?.invoke()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (centered) Arrangement.Center else Arrangement.Top,
        ) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().then(screenEntrance()), content = content)
        }
    }
}

@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = alignment, content = content)
    }
}

@Composable
internal fun ScreenTitle(text: String, modifier: Modifier = Modifier, centered: Boolean = false) {
    Text(text, modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start)
}

@Composable
internal fun StatusMark(icon: ImageVector, containerColor: Color, contentColor: Color) {
    Box(Modifier.size(56.dp).background(containerColor, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(25.dp))
    }
}

@Composable
internal fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    busy: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
) {
    Button(onClick = onClick, enabled = enabled && !busy, shape = MaterialTheme.shapes.medium,
        colors = colors, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun InitialsAvatar(name: String, size: Dp, style: TextStyle, modifier: Modifier = Modifier) {
    val initials = remember(name) {
        name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase()
    }
    Box(modifier.size(size).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center) {
        Text(initials, style = style, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun DarkSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val bars = WindowCompat.getInsetsController(window, view)
        val lightStatus = bars.isAppearanceLightStatusBars
        val lightNavigation = bars.isAppearanceLightNavigationBars
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
        onDispose {
            bars.isAppearanceLightStatusBars = lightStatus
            bars.isAppearanceLightNavigationBars = lightNavigation
        }
    }
}

@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.heightIn(min = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
        color = LocalExtraColors.current.dangerText,
        style = MaterialTheme.typography.bodyMedium,
    )
}

fun shareMeeting(context: Context, id: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "Присоединяйтесь к видеовстрече: ${MeetingLinks.url(id)}")
    context.startActivity(Intent.createChooser(send, "Отправить ссылку на встречу"))
}

fun copyMeetingLink(context: Context, id: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("Ссылка на встречу", MeetingLinks.url(id)))
}

/** Русские формы: plural(3, "человек", "человека", "человек"). */
fun plural(n: Int, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && (mod100 < 12 || mod100 > 14) -> few
        else -> many
    }
}
