package ro.solarcasa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ro.solarcasa.data.Balance

private val Light = lightColorScheme(
    primary = Color(0xFF1E5B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4EFC8),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF7A5900),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDF9E),
    onSecondaryContainer = Color(0xFF261A00),
    tertiary = Color(0xFF2B5F86),
    tertiaryContainer = Color(0xFFCCE5FF),
    onTertiaryContainer = Color(0xFF001D31),
    background = Color(0xFFF7FAF5),
    surface = Color(0xFFF7FAF5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF98D3AD),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF00522C),
    onPrimaryContainer = Color(0xFFB4EFC8),
    secondary = Color(0xFFF0C048),
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = Color(0xFF99CBF4),
    tertiaryContainer = Color(0xFF0B4A6E),
    onTertiaryContainer = Color(0xFFCCE5FF),
)

/** High contrast scheme for the emergency mode (dark room, stress). */
val EmergencyColors = darkColorScheme(
    primary = Color(0xFFFFD54F),
    onPrimary = Color.Black,
    background = Color.Black,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    onBackground = Color.White,
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFE6E6E6),
    error = Color(0xFFFF8A80),
)

@Composable
fun SolarCasaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Semantic status colors. They always appear together with an icon and a text (WCAG 1.4.1). */
data class StatusColors(val container: Color, val content: Color)

@Composable
fun statusColors(balance: Balance): StatusColors {
    val dark = isSystemInDarkTheme()
    return when (balance) {
        Balance.Surplus -> if (dark) StatusColors(Color(0xFF0F3D22), Color(0xFFA6F2BF)) else StatusColors(Color(0xFFD3F5DD), Color(0xFF0B5A2B))
        Balance.Even -> if (dark) StatusColors(Color(0xFF4A3800), Color(0xFFFFE08A)) else StatusColors(Color(0xFFFFF0C2), Color(0xFF6A4B00))
        Balance.Deficit -> if (dark) StatusColors(Color(0xFF5C1A15), Color(0xFFFFB4AB)) else StatusColors(Color(0xFFFFDAD6), Color(0xFF93000A))
    }
}
