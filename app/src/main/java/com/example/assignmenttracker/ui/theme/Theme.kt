package com.example.assignmenttracker.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

// State controller accessible anywhere in Compose
class ThemeController(
    val isDark: State<Boolean>,
    val toggleTheme: () -> Unit
)

val LocalThemeController = staticCompositionLocalOf<ThemeController> {
    error("LocalThemeController not provided")
}

@Composable
fun AssignmentTrackerTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()

    // Persist choice across app restarts using SharedPreferences
    val prefs = remember { context.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE) }
    val isDarkState = remember {
        mutableStateOf(prefs.getBoolean("is_dark_theme", systemInDark))
    }

    val controller = remember {
        ThemeController(
            isDark = isDarkState,
            toggleTheme = {
                val nextValue = !isDarkState.value
                isDarkState.value = nextValue
                prefs.edit().putBoolean("is_dark_theme", nextValue).apply()
            }
        )
    }

    val colorScheme = if (isDarkState.value) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalThemeController provides controller) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

// Reusable Light / Dark mode toggle icon button
@Composable
fun ThemeToggleButton(modifier: Modifier = Modifier) {
    val controller = LocalThemeController.current
    IconButton(
        onClick = { controller.toggleTheme() },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (controller.isDark.value) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = if (controller.isDark.value) "Switch to Light Mode" else "Switch to Dark Mode",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}