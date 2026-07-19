package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ১. Default Theme (Dark Royal Blue)
private val DefaultColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),      // Vivid Royal Blue
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF60A5FA),    // Light Royal Blue Accent
    background = Color(0xFF0A142F),   // Plain Dark Royal Blue Background
    surface = Color(0xFF121E3F),      // Sleek Matching Deep Royal Blue Surface Card
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFE2E8F0),
    error = Color(0xFFEF4444)         // Vibrant Red
)

// ২. Classic Light Theme
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F172A),      // Slate Dark
    onPrimary = Color.White,
    secondary = Color(0xFF3B82F6),    // Blue
    background = Color(0xFFF8FAFC),   // Ice White
    surface = Color(0xFFFFFFFF),      // Pure White Card
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    error = Color(0xFFEF4444)
)

// ৩. Emerald Green Theme (Rich Organic Dark Green)
private val EmeraldColorScheme = darkColorScheme(
    primary = Color(0xFF10B981),      // Emerald Green
    onPrimary = Color(0xFF041912),
    secondary = Color(0xFF34D399),    // Light Green
    background = Color(0xFF041912),   // Deep Forest Green
    surface = Color(0xFF0A2D21),      // Emerald Card
    onBackground = Color(0xFFF1FDF9),
    onSurface = Color(0xFFF1FDF9),
    error = Color(0xFFF43F5E)
)

// ৪. Modern Dark Slate Theme (Carbon)
private val CarbonColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),      // Blue Accent
    onPrimary = Color(0xFF0F1115),
    secondary = Color(0xFF60A5FA),    // Light Blue
    background = Color(0xFF0F1115),   // Carbon Black
    surface = Color(0xFF1E222B),      // Slate Grey Card
    onBackground = Color(0xFFF3F4F6),
    onSurface = Color(0xFFF3F4F6),
    error = Color(0xFFEF4444)
)

// ৫. Glassmorphism Theme (Dark Space Indigo with glowing gradients)
private val GlassColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),      // Indigo Accent
    onPrimary = Color(0xFF0B0F19),
    secondary = Color(0xFF38BDF8),    // Sky Blue
    background = Color(0xFF0B0F19),   // Deep Cosmic Space
    surface = Color(0xFF1E293B),      // Translucent Card (will be rendered with opacity in UI)
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    error = Color(0xFFF43F5E)
)

@Composable
fun MyApplicationTheme(
    themeName: String = "DEFAULT",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeName) {
        "LIGHT" -> LightColorScheme
        "EMERALD" -> EmeraldColorScheme
        "CARBON" -> CarbonColorScheme
        "GLASS" -> GlassColorScheme
        else -> DefaultColorScheme // "DEFAULT"
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
