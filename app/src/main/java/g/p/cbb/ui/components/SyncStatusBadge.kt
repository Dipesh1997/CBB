package g.p.cbb.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import g.p.cbb.ui.theme.Success
import g.p.cbb.ui.theme.Warning

@Composable
fun SyncStatusBadge(
    isSyncing: Boolean,
    isError: Boolean,
    lastSyncText: String,
    onManualSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = modifier
            .background(Color(0xFFE7E0EB), shape = RoundedCornerShape(20.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.08f), shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val dotColor = when {
            isError -> Color.Red
            isSyncing -> Color(0xFF0284C7)
            else -> Success
        }

        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(if (isSyncing) alpha else 1f)
                .background(dotColor, CircleShape)
        )

        Text(
            text = when {
                isSyncing -> "Syncing..."
                isError -> "Sync Paused"
                else -> lastSyncText.ifEmpty { "Live Auto-Sync" }
            },
            fontSize = 11.sp,
            color = Color(0xFF49454F)
        )

        IconButton(
            onClick = onManualSync,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Sync Now",
                tint = Color(0xFF49454F),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
