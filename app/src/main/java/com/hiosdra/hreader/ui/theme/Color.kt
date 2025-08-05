package com.hiosdra.hreader.ui.theme

import androidx.compose.ui.graphics.Color

// Reading-optimized color palette
// Primary colors - Warm and inviting
val Primary80 = Color(0xFF4FC3F7) // Light Cyan for primary actions
val Primary40 = Color(0xFF0277BD) // Deep Blue for primary actions
val PrimaryContainer80 = Color(0xFFE3F2FD) // Very light blue container
val PrimaryContainer40 = Color(0xFF01579B) // Dark blue container

// Secondary colors - Complementary reading tones
val Secondary80 = Color(0xFF81C784) // Light Green for secondary actions
val Secondary40 = Color(0xFF2E7D32) // Deep Green for secondary actions
val SecondaryContainer80 = Color(0xFFE8F5E8) // Very light green container
val SecondaryContainer40 = Color(0xFF1B5E20) // Dark green container

// Tertiary colors - Warm accent
val Tertiary80 = Color(0xFFFFB74D) // Light Orange for highlights
val Tertiary40 = Color(0xFFE65100) // Deep Orange for highlights
val TertiaryContainer80 = Color(0xFFFFF3E0) // Very light orange container
val TertiaryContainer40 = Color(0xFFBF360C) // Dark orange container

// Dark theme colors - Optimized for reading
val DarkBackground = Color(0xFF121212) // True dark for OLED
val DarkSurface = Color(0xFF1E1E1E) // Elevated surface
val DarkSurfaceVariant = Color(0xFF2D2D2D) // Card backgrounds
val DarkOnBackground = Color(0xFFE8E8E8) // Primary text
val DarkOnSurface = Color(0xFFE0E0E0) // Secondary text
val DarkOnSurfaceVariant = Color(0xFFB8B8B8) // Tertiary text
val DarkOutline = Color(0xFF3F3F3F) // Dividers and borders

// Light theme colors - Optimized for reading
val LightBackground = Color(0xFFFEFEFE) // Warm white
val LightSurface = Color(0xFFFFFFFF) // Pure white for cards
val LightSurfaceVariant = Color(0xFFF5F5F5) // Light gray for elevated surfaces
val LightOnBackground = Color(0xFF1A1A1A) // Primary text
val LightOnSurface = Color(0xFF2E2E2E) // Secondary text
val LightOnSurfaceVariant = Color(0xFF606060) // Tertiary text
val LightOutline = Color(0xFFE0E0E0) // Dividers and borders

// Reading-specific colors
val ReadStatusGreen = Color(0xFF4CAF50) // For read articles
val UnreadStatusBlue = Color(0xFF2196F3) // For unread articles
val AuthorAccent = Color(0xFF00ACC1) // Author names
val DateSubtle = Color(0xFF757575) // Timestamps
val WarningOrange = Color(0xFFFF9800) // Warnings/alerts
val ErrorRed = Color(0xFFE53935) // Errors

// Legacy colors (maintained for compatibility)
val MainBackground = DarkBackground
val MainDivider = DarkOutline
val MainSurface = DarkSurface
val MainAuthor = AuthorAccent
val MainDate = DateSubtle
val MainTitle = DarkOnBackground
val MainPreview = DarkOnSurfaceVariant
val MainChecked = ReadStatusGreen
val MainUnchecked = DateSubtle
val MainHeader = Color(0xFFBDBDBD)
