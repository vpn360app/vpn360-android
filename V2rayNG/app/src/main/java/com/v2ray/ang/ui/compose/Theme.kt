package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// VPN 360 palette "Vino y crema": wine primary, gold accent (connected), cream background.
private val LightColor = lightColorScheme(
    primary = Color(0xFF8E1B3A), // Wine
    onPrimary = Color(0xFFFFFFFF), // White
    primaryContainer = Color(0xFFFFD9DF), // Rose cream
    onPrimaryContainer = Color(0xFF3E0014), // Deep wine
    secondary = Color(0xFF7A5A12), // Deep gold (links, small details)
    onSecondary = Color(0xFFFFFFFF), // White
    secondaryContainer = Color(0xFFF6E3B8), // Pale gold
    onSecondaryContainer = Color(0xFF2A1C00), // Dark brown
    tertiary = Color(0xFFC8962E), // Gold (connected)
    onTertiary = Color(0xFF2A1215), // Ink
    tertiaryContainer = Color(0xFFF6E3B8), // Pale gold
    onTertiaryContainer = Color(0xFF2A1C00), // Dark brown
    error = Color(0xFFBA1A1A), // Red
    errorContainer = Color(0xFFFFDAD6), // Light Red
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF410002), // Dark Red
    background = Color(0xFFFBF6EF), // Cream
    onBackground = Color(0xFF2A1215), // Ink
    surface = Color(0xFFFFFFFF), // White
    onSurface = Color(0xFF2A1215), // Ink
    surfaceVariant = Color(0xFFF3E9E3), // Warm cream
    onSurfaceVariant = Color(0xFF6E5A5C), // Muted wine gray
    outline = Color(0xFF8C7A7C), // Medium warm gray
    outlineVariant = Color(0xFFE3D5D1), // Light warm gray
    inverseSurface = Color(0xFF3A2A2C), // Dark wine gray
    inverseOnSurface = Color(0xFFFBF1EE), // Cream
    inversePrimary = Color(0xFFFFB1C0), // Light rose
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF000000), // Black
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFFDF8F3), // Cream
    surfaceContainer = Color(0xFFF7EFE9), // Cream
    surfaceContainerHigh = Color(0xFFF2E8E2), // Warm cream
    surfaceContainerHighest = Color(0xFFECE1DB), // Warm cream
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFFFFB1C0), // Light rose
    onPrimary = Color(0xFF5C0F25), // Deep wine
    primaryContainer = Color(0xFF721530), // Wine
    onPrimaryContainer = Color(0xFFFFD9DF), // Rose cream
    secondary = Color(0xFFE9C27A), // Light gold
    onSecondary = Color(0xFF3F2D00), // Dark gold
    secondaryContainer = Color(0xFF5C430A), // Gold brown
    onSecondaryContainer = Color(0xFFF6E3B8), // Pale gold
    tertiary = Color(0xFFE0B45A), // Gold (connected)
    onTertiary = Color(0xFF2A1C00), // Dark brown
    tertiaryContainer = Color(0xFF5C430A), // Gold brown
    onTertiaryContainer = Color(0xFFF6E3B8), // Pale gold
    error = Color(0xFFFFB4AB), // Light Red
    errorContainer = Color(0xFF93000A), // Dark Red
    onError = Color(0xFF690005), // Deep Red
    onErrorContainer = Color(0xFFFFDAD6), // Light Red
    background = Color(0xFF1E1214), // Very dark wine
    onBackground = Color(0xFFF3E6DF), // Cream
    surface = Color(0xFF1E1214), // Very dark wine
    onSurface = Color(0xFFF3E6DF), // Cream
    surfaceVariant = Color(0xFF4A3A3C), // Dark wine gray
    onSurfaceVariant = Color(0xFFD8C2C3), // Light warm gray
    outline = Color(0xFFA08C8D), // Warm gray
    outlineVariant = Color(0xFF4A3A3C), // Dark wine gray
    inverseSurface = Color(0xFFF3E6DF), // Cream
    inverseOnSurface = Color(0xFF2A1215), // Ink
    inversePrimary = Color(0xFF8E1B3A), // Wine
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFFFFB1C0), // Light rose
    surfaceContainerLowest = Color(0xFF170C0E), // Near black wine
    surfaceContainerLow = Color(0xFF24171A), // Dark wine
    surfaceContainer = Color(0xFF291B1E), // Dark wine
    surfaceContainerHigh = Color(0xFF342528), // Dark wine
    surfaceContainerHighest = Color(0xFF3F2F32), // Dark wine
)

// Semantic Colors
val colorPing = Color(0xFF009966) // Green
val colorPingRed = Color(0xFFFF0099) // Pink Red
val colorConfigType = Color(0xFF7A5A12) // Deep gold detail
val colorFabActive = Color(0xFF8E1B3A) // VPN 360 wine (connected, advanced mode)
val colorFabInactiveLight = Color(0xFF9C9C9C) // Gray
val colorFabInactiveDark = Color(0xFF646464) // Dark Gray
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray
val dividerColorDark = Color(0xFF424242) // Dark Gray

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "1") ?: "1"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "1") ?: "1"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
