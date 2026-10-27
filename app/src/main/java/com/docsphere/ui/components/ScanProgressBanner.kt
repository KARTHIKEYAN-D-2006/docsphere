package com.docsphere.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.docsphere.data.model.ScanProgress
import com.docsphere.ui.theme.*

@Composable
fun ScanProgressBanner(
    progress: ScanProgress,
    modifier: Modifier = Modifier
) {
    val visible = progress !is ScanProgress.Idle && progress !is ScanProgress.Done

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        modifier = modifier
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "scan_spin")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing)
            ),
            label = "icon_rotation"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NavyMedium.copy(alpha = 0.95f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (progress) {
                is ScanProgress.Scanning -> {
                    ScanRow(
                        icon = { Icon(Icons.Default.Sync, null, tint = ElectricBlue,
                            modifier = Modifier.size(18.dp).rotate(rotation)) },
                        title = "Scanning files…",
                        subtitle = progress.currentFile.take(40),
                        current = progress.current,
                        total = progress.total,
                        color = ElectricBlue
                    )
                }
                is ScanProgress.Indexing -> {
                    ScanRow(
                        icon = { Icon(Icons.Default.Sync, null, tint = TealEmerald,
                            modifier = Modifier.size(18.dp).rotate(rotation)) },
                        title = "Indexing: ${progress.current} / ${progress.total}",
                        subtitle = progress.currentFile.take(40),
                        current = progress.current,
                        total = progress.total,
                        color = TealEmerald
                    )
                }
                is ScanProgress.Done -> {
                    Text(
                        text = "✅ Indexed ${progress.totalIndexed} files",
                        style = MaterialTheme.typography.bodySmall,
                        color = TealEmerald
                    )
                }
                is ScanProgress.Error -> {
                    Text(
                        text = "❌ ${progress.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun ScanRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    current: Int,
    total: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon()
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
        if (total > 0) {
            LinearProgressIndicator(
                progress = { current.toFloat() / total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = color.copy(alpha = 0.2f)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = color.copy(alpha = 0.2f)
            )
        }
    }
}
