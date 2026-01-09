package com.tailsync.app.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Premium Material You Dark Theme Colors
// Inspired by Samsung One UI / Google Material You with Apple-level polish
// =============================================================================

// Primary Brand Colors
val TailSyncPrimary = Color(0xFF8AB4F8)        // Material You Blue (soft, premium)
val TailSyncPrimaryDark = Color(0xFF669DF6)    // Darker blue for pressed states
val TailSyncPrimaryContainer = Color(0xFF004A77) // Container background

// Secondary / Accent Colors
val TailSyncSecondary = Color(0xFF80CBC4)      // Teal accent (elegant)
val TailSyncTertiary = Color(0xFFA8DAB5)       // Soft green

// Background Colors - True dark with subtle elevation
val DarkBackground = Color(0xFF0F0F0F)         // Near-black, OLED friendly
val DarkSurface = Color(0xFF1A1A1A)            // Card surfaces
val DarkSurfaceVariant = Color(0xFF232323)     // Elevated surfaces
val DarkSurfaceElevated = Color(0xFF2A2A2A)    // Highest elevation

// Text Colors - Optimized contrast for readability
val DarkOnBackground = Color(0xFFF5F5F5)       // Primary text - off-white
val DarkOnSurface = Color(0xFFE8E8E8)          // Surface text
val DarkOnSurfaceVariant = Color(0xFF9E9E9E)   // Secondary/muted text
val DarkOutline = Color(0xFF3D3D3D)            // Borders and dividers

// Status Colors - Vibrant but refined
val StatusConnected = Color(0xFF81C784)        // Green - success
val StatusDisconnected = Color(0xFFE57373)     // Red - error  
val StatusConnecting = Color(0xFFFFD54F)       // Amber - pending
val StatusInfo = Color(0xFF64B5F6)             // Blue - info

// Glassmorphism / Frosted glass effects
val GlassBackground = Color(0x14FFFFFF)        // 8% white overlay
val GlassBorder = Color(0x29FFFFFF)            // 16% white border
val GlassHighlight = Color(0x0DFFFFFF)         // 5% white shine

// Gradient accent colors for premium buttons
val GradientStart = Color(0xFF7C4DFF)          // Purple
val GradientEnd = Color(0xFF448AFF)            // Blue
