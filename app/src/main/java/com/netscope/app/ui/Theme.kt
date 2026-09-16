package com.netscope.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF0B7285)
private val TealLight = Color(0xFF3BA7B8)
private val Amber = Color(0xFFE8A33D)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = TealLight,
    tertiary = Amber,
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    secondary = Teal,
    tertiary = Amber,
)

@Composable
fun NetScopeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
