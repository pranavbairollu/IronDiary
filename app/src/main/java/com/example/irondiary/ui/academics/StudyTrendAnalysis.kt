package com.example.irondiary.ui.academics

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.irondiary.data.model.StudySession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StudyTrendAnalysis(sessions: List<StudySession>) {
    if (sessions.isEmpty()) return

    val analysis = remember(sessions) {
        analyzeStudyTrends(sessions)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Trend Analysis",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        TrendItem(
            icon = Icons.Default.TrendingUp,
            title = "Peak Study Time",
            subtitle = analysis.peakTimeRange,
            description = analysis.focusTip,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TrendItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    color: androidx.compose.ui.graphics.Color
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = color.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.headlineSmall, color = color)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class StudyAnalysis(
    val peakTimeRange: String,
    val focusTip: String
)

private fun analyzeStudyTrends(sessions: List<StudySession>): StudyAnalysis {
    val hourlyDistribution = sessions.groupBy { session ->
        Instant.ofEpochSecond(session.date.seconds)
            .atZone(ZoneId.systemDefault())
            .hour
    }.mapValues { (_, sessionsInHour) ->
        sessionsInHour.sumOf { it.duration }
    }

    val peakHour = hourlyDistribution.maxByOrNull { it.value }?.key ?: 0
    val formatter = DateTimeFormatter.ofPattern("ha")
    val startTime = java.time.LocalTime.of(peakHour, 0).format(formatter)
    val endTime = java.time.LocalTime.of(peakHour, 59).format(formatter)

    val peakTimeRange = "$startTime - $endTime"

    val focusTip = when (peakHour) {
        in 5..11 -> "You're a morning lark! Tackle your most challenging subjects before lunch when your brain is sharpest."
        in 12..17 -> "Afternoon focus is your strength. A great time for steady progress on consistent tasks."
        in 18..21 -> "Evening energy! Perfect for reviewing the day's work and planning for tomorrow."
        else -> "Night owl detected. Make sure you're getting enough rest, but use this quiet time for deep focus."
    }

    return StudyAnalysis(peakTimeRange, focusTip)
}
