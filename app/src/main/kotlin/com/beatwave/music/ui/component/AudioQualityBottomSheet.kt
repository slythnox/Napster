/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatwave.music.R
import com.beatwave.music.constants.AudioQuality

enum class NetworkQualityTab {
    WIFI,
    MOBILE
}

private data class QualityOption(
    val quality: AudioQuality,
    val title: String,
    val badge: String,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQualityBottomSheet(
    wifiQuality: AudioQuality,
    mobileQuality: AudioQuality,
    onWifiQualitySelected: (AudioQuality) -> Unit,
    onMobileQualitySelected: (AudioQuality) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: NetworkQualityTab = NetworkQualityTab.WIFI,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTab by remember { mutableStateOf(initialTab) }

    val options = remember {
        listOf(
            QualityOption(
                quality = AudioQuality.LOSSLESS,
                title = "Prefer Lossless",
                badge = "FLAC PREFERRED",
                description = "Prefers lossless FLAC when supported by source · Does not guarantee lossless source or output"
            ),
            QualityOption(
                quality = AudioQuality.HIGH,
                title = "High Quality",
                badge = "UP TO 320 KBPS",
                description = "Highest bitrate stream · 320kbps / Opus / AAC"
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

    val currentQuality = if (activeTab == NetworkQualityTab.WIFI) wifiQuality else mobileQuality

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
                        text = "Audio Quality",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Set streaming quality for Wi-Fi and Phone Data",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp
                        ),
                        color = Color(0xFF9EACB9)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Network Type Segmented Selector
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1A1D24),
                border = BorderStroke(1.dp, Color(0xFF252932)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Wi-Fi Segment
                    val isWifiActive = activeTab == NetworkQualityTab.WIFI
                    val wifiBgColor by animateColorAsState(
                        targetValue = if (isWifiActive) Color(0xFF26354A) else Color.Transparent,
                        animationSpec = tween(200),
                        label = "wifiBg"
                    )
                    val wifiBorderColor by animateColorAsState(
                        targetValue = if (isWifiActive) Color(0xFF3B82F6).copy(alpha = 0.6f) else Color.Transparent,
                        animationSpec = tween(200),
                        label = "wifiBorder"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(wifiBgColor)
                            .border(BorderStroke(1.dp, wifiBorderColor), RoundedCornerShape(10.dp))
                            .clickable { activeTab = NetworkQualityTab.WIFI }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.wifi_proxy),
                                contentDescription = null,
                                tint = if (isWifiActive) Color(0xFF93C5FD) else Color(0xFF8E95A2),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Wi-Fi",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isWifiActive) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                ),
                                color = if (isWifiActive) Color.White else Color(0xFF8E95A2)
                            )
                        }
                    }

                    // Mobile Data Segment
                    val isMobileActive = activeTab == NetworkQualityTab.MOBILE
                    val mobileBgColor by animateColorAsState(
                        targetValue = if (isMobileActive) Color(0xFF26354A) else Color.Transparent,
                        animationSpec = tween(200),
                        label = "mobileBg"
                    )
                    val mobileBorderColor by animateColorAsState(
                        targetValue = if (isMobileActive) Color(0xFF3B82F6).copy(alpha = 0.6f) else Color.Transparent,
                        animationSpec = tween(200),
                        label = "mobileBorder"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(mobileBgColor)
                            .border(BorderStroke(1.dp, mobileBorderColor), RoundedCornerShape(10.dp))
                            .clickable { activeTab = NetworkQualityTab.MOBILE }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.network_update),
                                contentDescription = null,
                                tint = if (isMobileActive) Color(0xFF93C5FD) else Color(0xFF8E95A2),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Phone Data",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isMobileActive) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                ),
                                color = if (isMobileActive) Color.White else Color(0xFF8E95A2)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (activeTab == NetworkQualityTab.WIFI) {
                    "Configure preferred streaming quality when connected to Wi-Fi networks."
                } else {
                    "Configure preferred streaming quality when using cellular mobile data."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                ),
                color = Color(0xFF7E8B99),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quality Selection Cards
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                options.forEach { item ->
                    val isSelected = currentQuality == item.quality

                    Surface(
                        onClick = {
                            if (activeTab == NetworkQualityTab.WIFI) {
                                onWifiQualitySelected(item.quality)
                            } else {
                                onMobileQualitySelected(item.quality)
                            }
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

            Spacer(modifier = Modifier.height(20.dp))

            // Done Button
            Button(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQualityBottomSheet(
    currentQuality: AudioQuality,
    onQualitySelected: (AudioQuality) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AudioQualityBottomSheet(
        wifiQuality = currentQuality,
        mobileQuality = currentQuality,
        onWifiQualitySelected = onQualitySelected,
        onMobileQualitySelected = onQualitySelected,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}
