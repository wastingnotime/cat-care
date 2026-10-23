package care.cat.offline

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

val Paper: Color
    @Composable get() = MaterialTheme.colorScheme.background
val Ink: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
val Forest: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Sage: Color
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
val Line: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant
val Rust: Color
    @Composable get() = MaterialTheme.colorScheme.error

@Composable
internal fun CareTheme(content: @Composable () -> Unit) {
    val activity = androidx.activity.compose.LocalActivity.current as ComponentActivity
    val preferences = remember { activity.getSharedPreferences("appearance", 0) }
    var choice by remember { mutableStateOf(preferences.getString("theme", "system") ?: "system") }
    DisposableEffect(preferences) {
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "theme") choice = preferences.getString("theme", "system") ?: "system"
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val dark = choice == "dark" || (choice == "system" && isSystemInDarkTheme())
    val scheme =
        if (dark)
            darkColorScheme(
                primary = Color(0xffa6d5bc),
                onPrimary = Color(0xff10382a),
                primaryContainer = Color(0xff2b4538),
                onPrimaryContainer = Color(0xffd1eadb),
                secondary = Color(0xffb7ccbd),
                onSecondary = Color(0xff21362b),
                secondaryContainer = Color(0xff2b4538),
                onSecondaryContainer = Color(0xffd1eadb),
                tertiary = Color(0xffa6d5bc),
                onTertiary = Color(0xff10382a),
                tertiaryContainer = Color(0xff2b4538),
                onTertiaryContainer = Color(0xffd1eadb),
                background = Color(0xff121a16),
                onBackground = Color(0xffe2e8df),
                surface = Color(0xff18221c),
                onSurface = Color(0xffe2e8df),
                surfaceVariant = Color(0xff2b382f),
                onSurfaceVariant = Color(0xffb9c7bc),
                surfaceContainer = Color(0xff18221c),
                surfaceContainerHigh = Color(0xff233128),
                surfaceContainerHighest = Color(0xff2b382f),
                surfaceContainerLow = Color(0xff18221c),
                surfaceContainerLowest = Color(0xff121a16),
                outline = Color(0xff86978a),
                outlineVariant = Color(0xff405044),
                error = Color(0xffffb59f),
                errorContainer = Color(0xff563026),
                onErrorContainer = Color(0xffffdbcf),
            )
        else
            lightColorScheme(
                primary = Color(0xff285345),
                onPrimary = Color.White,
                primaryContainer = Color(0xffe9eee4),
                onPrimaryContainer = Color(0xff285345),
                secondary = Color(0xff285345),
                secondaryContainer = Color(0xffe9eee4),
                onSecondaryContainer = Color(0xff243c32),
                tertiary = Color(0xff285345),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xffe9eee4),
                onTertiaryContainer = Color(0xff285345),
                background = Color(0xfff8f7f2),
                onBackground = Color(0xff243c32),
                surface = Color(0xfff8f7f2),
                onSurface = Color(0xff243c32),
                surfaceVariant = Color(0xffe9eee4),
                onSurfaceVariant = Color(0xff5c6b60),
                surfaceContainer = Color(0xfff8f7f2),
                surfaceContainerHigh = Color(0xfff8f7f2),
                surfaceContainerHighest = Color(0xffe9eee4),
                surfaceContainerLow = Color(0xfff8f7f2),
                surfaceContainerLowest = Color.White,
                outline = Color(0xff8c998d),
                outlineVariant = Color(0xffdfe5da),
                error = Color(0xff954c38),
                errorContainer = Color(0xfff8e9e1),
                onErrorContainer = Color(0xff713623),
            )
    SideEffect {
        val style =
            if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                )
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
internal fun ThemeChoice() {
    val preferences = LocalContext.current.getSharedPreferences("appearance", 0)
    var choice by remember { mutableStateOf(preferences.getString("theme", "system") ?: "system") }
    Choice(
        tr(R.string.theme),
        linkedMapOf(
            "system" to tr(R.string.theme_system),
            "light" to tr(R.string.theme_light),
            "dark" to tr(R.string.theme_dark),
        ),
        choice,
    ) {
        choice = it
        preferences.edit { putString("theme", it) }
    }
}
