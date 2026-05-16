package com.example.irondiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RecordTrophyCard(
    exercise: String,
    weight: Double,
    unit: String,
    date: String,
    isRecent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isHeavy = weight >= 100.0
    val showGlow = isHeavy || isRecent
    
    Box(modifier = modifier) {
        // Dynamic Glow Effect (Subtle for Light Theme)
        if (showGlow) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(180.dp)
                    .padding(4.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                (if (isHeavy) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary).copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            )
        }

        Card(
            modifier = Modifier
                .width(160.dp)
                .height(180.dp)
                .border(
                    width = 1.dp,
                    brush = when {
                        isHeavy -> Brush.linearGradient(listOf(Color(0xFFFFD700).copy(alpha = 0.4f), Color.Transparent))
                        isRecent -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), Color.Transparent))
                        else -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), Color.Transparent))
                    },
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isHeavy -> Color(0xFFFFD700).copy(alpha = 0.05f)
                    isRecent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isHeavy) 8.dp else 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    when {
                                        isHeavy -> Color(0xFFFFD700).copy(alpha = 0.2f)
                                        isRecent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = when {
                            isHeavy -> Color(0xFFFFA000) // Deep Gold
                            isRecent -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = exercise.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 14.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "$weight $unit",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
