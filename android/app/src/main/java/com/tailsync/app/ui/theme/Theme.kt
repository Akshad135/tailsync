package com.tailsync.app.ui.theme

import android.app.Activity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// =============================================================================
// Premium Dark Color Scheme - Single Theme, No Light Mode
// =============================================================================
private val DarkColorScheme = darkColorScheme(
    // Primary colors
    primary = TailSyncPrimary,
    onPrimary = Color.Black,
    primaryContainer = TailSyncPrimaryContainer,
    onPrimaryContainer = TailSyncPrimary,
    
    // Secondary colors
    secondary = TailSyncSecondary,
    onSecondary = Color.Black,
    secondaryContainer = TailSyncSecondary.copy(alpha = 0.15f),
    onSecondaryContainer = TailSyncSecondary,
    
    // Tertiary colors  
    tertiary = TailSyncTertiary,
    onTertiary = Color.Black,
    tertiaryContainer = TailSyncTertiary.copy(alpha = 0.15f),
    onTertiaryContainer = TailSyncTertiary,
    
    // Background & Surface
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    
    // Other colors
    outline = DarkOutline,
    outlineVariant = DarkOutline.copy(alpha = 0.5f),
    error = StatusDisconnected,
    onError = Color.Black,
    errorContainer = StatusDisconnected.copy(alpha = 0.2f),
    onErrorContainer = StatusDisconnected
)

// =============================================================================
// Animation Specs - Apple-quality smooth physics
// =============================================================================
object TailSyncAnimation {
    // Standard spring - smooth and responsive
    val Standard = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    // Gentle spring - for subtle movements
    val Gentle = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
    
    // Snappy spring - for quick feedback
    val Snappy = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    // Duration constants (ms)
    const val FAST = 150
    const val MEDIUM = 300
    const val SLOW = 500
    
    // Stagger delay for list animations
    const val STAGGER_DELAY = 50L
}

// =============================================================================
// Main Theme Composable
// =============================================================================
@Composable
fun TailSyncTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent system bars for edge-to-edge
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            
            // Light icons on dark background
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
