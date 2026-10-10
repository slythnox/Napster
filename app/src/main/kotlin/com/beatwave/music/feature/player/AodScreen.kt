/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.feature.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.beatwave.music.LocalPlayerConnection
import com.beatwave.music.R
import com.beatwave.music.constants.AodDimLevelKey
import com.beatwave.music.constants.AodPixelShiftEnabledKey
import com.beatwave.music.core.extensions.togglePlayPause
import com.beatwave.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Minimal Standby Landscape Music Display.
 *
 * Designed around restrained editorial minimalism:
 * - Pure black AMOLED background
 * - Top-left small digital clock (respects 12/24h setting, isolated recomposition)
 * - Prominent square album artwork with circular record-inspired graphics
 * - Centred track title & artist
 * - Minimal monochrome playback controls (previous, play/pause, next)
 * - Deliberate horizontal swipe (left/right) or system Back exits Standby
 * - Auto-locks into landscape orientation while displayed
 */
@Composable
fun AodScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()

    val pixelShiftEnabled by rememberPreference(AodPixelShiftEnabledKey, true)
    val (dimLevel, _) = rememberPreference(AodDimLevelKey, 0.75f)

    // Dedicated Landscape Orientation Lifecycle
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            activity?.requestedOrientation = previousOrientation
        }
    }

    // Keep screen on and hide system bars while Standby is active
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Anti-burn-in pixel shifting (updates every 60 seconds)
    var shiftX by remember { mutableFloatStateOf(0f) }
    var shiftY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pixelShiftEnabled) {
        var step = 0
        while (isActive) {
            if (pixelShiftEnabled) {
                step = (step + 1) % 8
                val angle = step * (Math.PI / 4.0)
                shiftX = (cos(angle) * 8.0).toFloat()
                shiftY = (sin(angle) * 8.0).toFloat()
            } else {
                shiftX = 0f
                shiftY = 0f
            }
            delay(60_000L)
        }
    }

    // Playback progress sampling (1s interval when playing)
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    LaunchedEffect(isPlaying, mediaMetadata) {
        while (isActive) {
            val dur = playerConnection.player.duration
            val pos = playerConnection.player.currentPosition
            if (dur > 0) durationMs = dur
            if (pos >= 0) positionMs = pos
            if (!isPlaying) break
            delay(1000L)
        }
    }
    val progressRatio = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // System Back exits Standby
    BackHandler {
        onDismiss()
    }

    // Horizontal swipe threshold calculation
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 120.dp.toPx() }

    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val artworkSize = (screenHeightDp * 0.38f).coerceIn(120.dp, 160.dp)
    val turntableDiameter = artworkSize * 1.7f

    val title = mediaMetadata?.title?.takeIf { it.isNotBlank() } ?: "No track playing"
    val artist = mediaMetadata?.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() } ?: "Napster"
    val displayTrackInfo = if (title == "No track playing") title else "$title $artist"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var totalDragX = 0f
                var totalDragY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragEnd = {
                        // Deliberate horizontal swipe (either left or right) exits Standby
                        if (abs(totalDragX) > swipeThresholdPx && abs(totalDragX) > abs(totalDragY) * 1.3f) {
                            onDismiss()
                        }
                    },
                    onDragCancel = {
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                    }
                )
            }
    ) {
        // Top-left digital clock (visually secondary, isolated recomposition)
        StandbyClock(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 28.dp, top = 20.dp)
                .alpha(dimLevel.coerceIn(0.2f, 1f))
        )

        // Centred music player stack
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(shiftX.roundToInt(), shiftY.roundToInt()) }
                .alpha(dimLevel.coerceIn(0.2f, 1f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Album artwork + Record turntable graphic
            Box(
                modifier = Modifier.size(turntableDiameter),
                contentAlignment = Alignment.Center
            ) {
                // Record turntable circular graphics
                RecordTurntableGraphic(
                    isRotating = isPlaying,
                    modifier = Modifier.fillMaxSize()
                )

                // Square album artwork
                if (mediaMetadata?.thumbnailUrl != null) {
                    AsyncImage(
                        model = mediaMetadata?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(artworkSize)
                            .clip(RoundedCornerShape(18.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(artworkSize)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF161616)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(artworkSize * 0.35f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Minimal progress indicator (pill under artwork)
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxWidth(progressRatio)
                        .background(Color.White)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Track information (centred, single line with ellipsis)
            Text(
                text = displayTrackInfo,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Minimal playback controls (previous, play/pause, next)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                IconButton(
                    onClick = { playerConnection.player.seekToPreviousMediaItem() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.skip_previous),
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = { playerConnection.player.togglePlayPause() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPlaying) R.drawable.pause else R.drawable.play
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { playerConnection.player.seekToNextMediaItem() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.skip_next),
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Isolated digital clock for Standby display.
 * Recomposes once per minute without invalidating the rest of the screen.
 */
@Composable
private fun StandbyClock(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var timeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (isActive) {
            val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
            val pattern = if (is24Hour) "HH:mm" else "h:mm"
            timeText = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern))
            val currentSecond = LocalTime.now().second
            val secondsToNextMinute = (60 - currentSecond).coerceAtLeast(1)
            delay(secondsToNextMinute * 1000L)
        }
    }

    Text(
        text = timeText,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.SansSerif,
        color = Color.White.copy(alpha = 0.85f),
        modifier = modifier
    )
}

/**
 * Lightweight vinyl/record-inspired circular graphics.
 * Uses thin subtle lines and four diagonal spokes.
 * Rotation is lifecycle-aware and pauses when playback is paused.
 */
@Composable
private fun RecordTurntableGraphic(
    isRotating: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VinylSpin")
    val rotationAngle by if (isRotating) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "VinylRotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val maxRadius = minOf(centerX, centerY)

        // Outer thin circle
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = maxRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.dp.toPx())
        )

        // Subtle inner groove ring
        drawCircle(
            color = Color.White.copy(alpha = 0.12f),
            radius = maxRadius * 0.82f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 0.75.dp.toPx())
        )

        // Four diagonal radial spoke lines (45°, 135°, 225°, 315°)
        rotate(degrees = rotationAngle, pivot = Offset(centerX, centerY)) {
            val angles = listOf(45.0, 135.0, 225.0, 315.0)
            val innerR = maxRadius * 0.52f
            val outerR = maxRadius * 0.98f

            for (angleDeg in angles) {
                val rad = Math.toRadians(angleDeg)
                val startX = centerX + (innerR * cos(rad)).toFloat()
                val startY = centerY + (innerR * sin(rad)).toFloat()
                val endX = centerX + (outerR * cos(rad)).toFloat()
                val endY = centerY + (outerR * sin(rad)).toFloat()

                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}
