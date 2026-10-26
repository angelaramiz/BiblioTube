package com.bibliotube.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun BiblioTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // dynamicColor = false a proposito: la marca manda (ver DESIGN.md).
    MaterialTheme(
        colorScheme = if (darkTheme) BtDarkColors else BtLightColors,
        typography = BtTypography,
        content = content
    )
}
