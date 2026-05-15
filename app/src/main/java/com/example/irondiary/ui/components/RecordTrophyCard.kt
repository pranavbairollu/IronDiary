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
        // Dynamic Glow Effect
        if (showGlow) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(180.dp)
                    .padding(4.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                (if (isHeavy) Color(0xFFFFD700) else Color(0xFF64FFDA)).copy(alpha = 0.15f),
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
                        isHeavy -> Brush.linearGradient(listOf(Color(0xFFFFD700).copy(alpha = 0.3f), Color.Transparent))
                        isRecent -> Brush.linearGradient(listOf(Color(0xFF64FFDA).copy(alpha = 0.3f), Color.Transparent))
                        else -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent))
                    },
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isHeavy) 12.dp else 4.dp)
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
                                        isHeavy -> Color(0xFFFFD700).copy(alpha = 0.3f)
                                        isRecent -> Color(0xFF64FFDA).copy(alpha = 0.3f)
                                        else -> Color.White.copy(alpha = 0.05f)
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
                            isHeavy -> Color(0xFFFFD700)
                            isRecent -> Color(0xFF64FFDA)
                            else -> Color.White.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = exercise.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "$weight $unit",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}
