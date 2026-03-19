package com.example.irondiary.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.irondiary.data.local.SyncState

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SyncIndicator(
    syncState: SyncState,
    modifier: Modifier = Modifier
) {
    Crossfade(targetState = syncState, label = "SyncIndicator") { state ->
        when (state) {
            SyncState.PENDING -> {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync Pending",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = modifier.size(16.dp)
                )
            }
            SyncState.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync Failed",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = modifier.size(16.dp)
                )
            }
            SyncState.SYNCED -> {
                // Subtle feedback: faint check or nothing
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Synced",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    modifier = modifier.size(16.dp)
                )
            }
            SyncState.DELETED -> {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Pending Deletion",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                    modifier = modifier.size(16.dp)
                )
            }
        }
    }
}

