package com.hiosdra.hreader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One corner-radius scale for the whole app, reached through `MaterialTheme.shapes`.
 *
 * extraSmall  shimmer bars and other hairline fills
 * small       thumbnails, menus
 * medium      cards, inline banners
 * large       full-width article images
 * extraLarge  pills: search fields, the selected subscription row
 *
 * Fully rounded elements (the unread dot, the reading-progress and credibility bars) use
 * `CircleShape` rather than a token here — their radius follows their own height, not this scale.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
