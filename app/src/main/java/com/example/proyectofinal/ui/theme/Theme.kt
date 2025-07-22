package com.example.proyectofinal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Esquema de colores para el TEMA OSCURO (recomendado para tus colores)
private val DarkColorScheme = darkColorScheme(
    primary = BrandTeal,
    onPrimary = BrandDarkBlue,
    secondary = BrandTeal,
    onSecondary = BrandDarkBlue,
    tertiary = BrandTeal,
    onTertiary = BrandDarkBlue,
    background = BrandDarkBlue,
    onBackground = BrandLightText,
    surface = BrandSurface,
    onSurface = BrandLightText,
    error = Color(0xFFCF6679), // Un rojo estándar para errores
    onError = Color(0xFF000000)
)

// Esquema de colores para un TEMA CLARO (opcional)
private val LightColorScheme = lightColorScheme(
    primary = BrandTeal,
    onPrimary = BrandDarkBlue,
    secondary = BrandTeal,
    onSecondary = BrandDarkBlue,
    background = Color(0xFFF7F7F7),
    onBackground = BrandDarkBlue,
    surface = Color(0xFFFFFFFF),
    onSurface = BrandDarkBlue
)

@Composable
fun ProyectoFinalTheme(
    // Forzamos el tema oscuro por defecto, ya que es el que mejor se ve con tus colores
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}