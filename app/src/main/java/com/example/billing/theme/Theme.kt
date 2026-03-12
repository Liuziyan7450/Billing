package com.example.billing.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF8C4A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDBA),
    secondary = Color(0xFF5D5E71),
    secondaryContainer = Color(0xFFE1E1F9),
    tertiary = Color(0xFF755A2F),
    tertiaryContainer = Color(0xFFFFDDAF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB871),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF6A3A00),
    secondary = Color(0xFFC4C5DD),
    secondaryContainer = Color(0xFF454659),
    tertiary = Color(0xFFE4C08D),
    tertiaryContainer = Color(0xFF5A431B)
)

@Composable
fun BillingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
