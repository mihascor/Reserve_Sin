package ru.reserve.sin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ReserveSinDarkColors = darkColorScheme(
    primary = Color(0xFFD8B4FE),
    onPrimary = Color(0xFF2D0B4E),
    primaryContainer = Color(0xFF493568),
    onPrimaryContainer = Color(0xFFF2E7FF),
    secondary = Color(0xFFD0C3DC),
    onSecondary = Color(0xFF352D3D),
    secondaryContainer = Color(0xFF4B4354),
    onSecondaryContainer = Color(0xFFEDE4F4),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE8E0E8),
    surface = Color(0xFF1D1B20),
    onSurface = Color(0xFFE8E0E8),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF958F99),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun ReserveSinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ReserveSinDarkColors,
        content = content,
    )
}
