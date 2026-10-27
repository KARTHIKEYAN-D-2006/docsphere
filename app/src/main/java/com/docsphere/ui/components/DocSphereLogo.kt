package com.docsphere.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docsphere.R
import com.docsphere.ui.theme.*

/**
 * Renders the DOCSPHERE icon emblem (vector sheet with dispersing digital blocks).
 */
@Composable
fun DocSphereEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ElectricBlue.copy(alpha = 0.25f),
                        NavyMedium
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(ElectricBlue.copy(alpha = 0.6f), Color.Transparent)
                ),
                shape = RoundedCornerShape(size * 0.22f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_docsphere_emblem),
            contentDescription = "DOCSPHERE Logo",
            tint = Color.Unspecified,
            modifier = Modifier.size(size * 0.8f)
        )
    }
}

/**
 * Top-level branding row featuring emblem + DOCSPHERE typography in ALL CAPS.
 */
@Composable
fun DocSphereLogoHeader(
    modifier: Modifier = Modifier,
    subtitle: String = "PERSONAL DOCUMENT INTELLIGENCE"
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DocSphereEmblem(size = 46.dp)
        Column {
            Text(
                text = "DOCSPHERE",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                ),
                color = TextPrimary
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = ElectricBlue
                )
            }
        }
    }
}

/**
 * High-impact brand banner using the generated official branding image.
 */
@Composable
fun DocSphereBrandCard(
    modifier: Modifier = Modifier,
    height: Dp = 150.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(NavyMedium)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(ElectricBlue.copy(alpha = 0.5f), NeonCyan.copy(alpha = 0.2f))
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.docsphere_logo),
            contentDescription = "DOCSPHERE BRAND",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
