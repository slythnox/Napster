/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatwave.music.R
import com.beatwave.music.constants.AudioQuality

private data class QualityOption(
    val quality: AudioQuality,
    val title: String,
    val badge: String,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQualityBottomSheet(
    currentQuality: AudioQuality,
    onQualitySelected: (AudioQuality) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val options = remember {
        listOf(
            QualityOption(
                quality = AudioQuality.HIGH,
                title = "High Quality",
                badge = "UP TO 256 KBPS",
                description = "Highest bitrate stream · Opus / AAC (Higher data use)"
            ),
            QualityOption(
                quality = AudioQuality.AUTO,
                title = "Auto (Adaptive)",
                badge = "ADAPTIVE",
                description = "Automatically adapts · High on Wi-Fi, saver on cellular"
            ),
            QualityOption(
                quality = AudioQuality.LOW,
                title = "Data Saver",
                badge = "UP TO 70 KBPS",
                description = "Reduced bitrate stream · Minimal data usage"
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xFF121418),
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        },
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            color = Color(0xFF1E232B),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.graphic_eq),
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Streaming Quality",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Select preferred audio resolution & bit depth",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp
                        ),
                        color = Color(0xFF9EACB9)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "If a track is unavailable in the chosen quality, the highest available quality will be streamed automatically.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                ),
                color = Color(0xFF7E8B99),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Quality Selection Cards
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                options.forEach { item ->
                    val isSelected = currentQuality == item.quality

                    Surface(
                        onClick = {
                            onQualitySelected(item.quality)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFF1E2634) else Color(0xFF16181D),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.55f) else Color(0xFF252932)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp
                                        ),
                                        color = if (isSelected) Color.White else Color(0xFFE2E4E9)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) Color(0xFF26354A) else Color(0xFF22262E)
                                    ) {
                                        Text(
                                            text = item.badge,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                letterSpacing = 0.5.sp
                                            ),
                                            color = if (isSelected) Color(0xFF93C5FD) else Color(0xFF8E95A2),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp
                                    ),
                                    color = if (isSelected) Color(0xFFB0C0D0) else Color(0xFF7C828D)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(color = Color(0xFF3B82F6), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.check),
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .border(width = 1.5.dp, color = Color(0xFF383D48), shape = CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
