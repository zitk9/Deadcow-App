package com.droid.dolphy.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DeadcowOrange = Color(0xFFFF8C00)
private val DeadcowOrangeLight = Color(0xFFFFB347)
private val DeadcowOrangeDark = Color(0xFFCC7000)

private val DarkColorScheme = darkColorScheme(
    primary = DeadcowOrange,
    secondary = DeadcowOrangeLight,
    tertiary = DeadcowOrangeDark
)

private val LightColorScheme = lightColorScheme(
    primary = DeadcowOrange,
    secondary = DeadcowOrangeLight,
    tertiary = DeadcowOrangeDark
)

@Composable
fun DolphyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
