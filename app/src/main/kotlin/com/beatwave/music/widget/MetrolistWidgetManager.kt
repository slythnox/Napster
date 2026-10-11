/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.beatwave.music.R
import com.beatwave.music.app.MainActivity
import com.beatwave.music.db.MusicDatabase
import com.beatwave.music.feature.lyrics.LyricsEntry
import com.beatwave.music.feature.lyrics.LyricsUtils
import com.beatwave.music.ui.component.shapes.ContinuousRoundedRectangle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

@Singleton
class vivimusicWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase
) {
    private val imageLoader by lazy {
        ImageLoader.Builder(context)
            .crossfade(false)
            .build()
    }

    // Cache for album art to avoid reloading
    private var cachedArtworkUri: String? = null
    private var cachedAlbumArt: Bitmap? = null
    private var cachedCircularAlbumArt: Bitmap? = null

    // Base disc cache for smooth vinyl turntable rotation
    private var cachedDiscArt: Bitmap? = null
    private var cachedBaseDisc: Bitmap? = null
    private var cachedDiscSize: Int = 0
    private val discBuffers = arrayOfNulls<Bitmap>(2)
    private var activeDiscBufferIndex = 0

    // State counters for playback visualisations (equaliser, vinyl angle)
    private var eqFrameCounter: Int = 0
    private var vinylRotationAngle: Float = 0f

    suspend fun updateWidgets(
        title: String,
        artist: String,
        artworkUri: String?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0,
        mediaId: String? = null,
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val hasTrack = mediaId != null && title.isNotBlank()

        // Advance animation frames when playing
        if (isPlaying && hasTrack) {
            eqFrameCounter = (eqFrameCounter + 1) % 120
            vinylRotationAngle = (vinylRotationAngle + 12f) % 360f
        }

        // Use cached album art if URI hasn't changed, otherwise load new one
        val albumArt: Bitmap?
        val circularAlbumArt: Bitmap?

        if (hasTrack) {
            if (artworkUri != null && artworkUri == cachedArtworkUri && cachedAlbumArt != null) {
                albumArt = cachedAlbumArt
                circularAlbumArt = cachedCircularAlbumArt
            } else {
                albumArt = artworkUri?.let { loadAlbumArt(it, 400) }
                circularAlbumArt = albumArt?.let { getCircularBitmap(it) }
                // Update cache
                cachedArtworkUri = artworkUri
                cachedAlbumArt = albumArt
                cachedCircularAlbumArt = circularAlbumArt
                cachedBaseDisc = null // Invalidate disc cache for new art
            }
        } else {
            albumArt = null
            circularAlbumArt = null
            cachedArtworkUri = null
            cachedAlbumArt = null
            cachedCircularAlbumArt = null
            cachedBaseDisc = null
        }

        // 1. Update Widget A: Dot Player (MusicWidgetReceiver)
        val dotComponent = ComponentName(context, MusicWidgetReceiver::class.java)
        val dotWidgetIds = appWidgetManager.getAppWidgetIds(dotComponent)
        if (dotWidgetIds.isNotEmpty()) {
            dotWidgetIds.forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val views = createDotPlayerViews(
                    options = options,
                    albumArt = albumArt,
                    isPlaying = isPlaying,
                    hasTrack = hasTrack,
                    frame = eqFrameCounter,
                )
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        // 2. Update Widget B: Lyrics Player (LyricsWidgetReceiver)
        val lyricsComponent = ComponentName(context, LyricsWidgetReceiver::class.java)
        val lyricsWidgetIds = appWidgetManager.getAppWidgetIds(lyricsComponent)
        if (lyricsWidgetIds.isNotEmpty()) {
            val lines = if (hasTrack) loadLyricLines(mediaId) else emptyList()
            lyricsWidgetIds.forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val views = createLyricsPlayerViews(
                    options = options,
                    albumArt = albumArt,
                    isPlaying = isPlaying,
                    hasTrack = hasTrack,
                    lines = lines,
                    currentPosition = currentPosition,
                    frame = eqFrameCounter,
                )
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        // 3. Update Widget C: Vinyl Deck (TurntableWidgetReceiver)
        val vinylComponent = ComponentName(context, TurntableWidgetReceiver::class.java)
        val vinylWidgetIds = appWidgetManager.getAppWidgetIds(vinylComponent)
        if (vinylWidgetIds.isNotEmpty()) {
            vinylWidgetIds.forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val views = createVinylDeckViews(
                    options = options,
                    title = title,
                    artist = artist,
                    albumArt = albumArt,
                    circularAlbumArt = circularAlbumArt,
                    isPlaying = isPlaying,
                    hasTrack = hasTrack,
                    duration = duration,
                    currentPosition = currentPosition,
                    rotationAngle = vinylRotationAngle,
                )
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    /**
     * Fast, lightweight partial update for Widget C (Vinyl Deck) to spin the turntable disc
     * smoothly without regenerating the entire widget hierarchy or allocating new bitmaps.
     */
    fun updateVinylRotationOnly(isPlaying: Boolean) {
        if (!isPlaying) return
        val baseDisc = cachedBaseDisc ?: return
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val vinylComponent = ComponentName(context, TurntableWidgetReceiver::class.java)
        val vinylWidgetIds = appWidgetManager.getAppWidgetIds(vinylComponent)
        if (vinylWidgetIds.isEmpty()) return

        vinylRotationAngle = (vinylRotationAngle + 3.0f) % 360f
        val discSize = cachedDiscSize.coerceIn(60, 260)

        // Alternate double buffer to eliminate runtime allocations
        activeDiscBufferIndex = (activeDiscBufferIndex + 1) % 2
        var buffer = discBuffers[activeDiscBufferIndex]
        if (buffer == null || buffer.width != discSize || buffer.height != discSize || buffer.isRecycled) {
            buffer = Bitmap.createBitmap(discSize, discSize, Bitmap.Config.ARGB_8888)
            discBuffers[activeDiscBufferIndex] = buffer
        } else {
            buffer.eraseColor(Color.TRANSPARENT)
        }

        val canvas = Canvas(buffer)
        val matrix = Matrix().apply {
            postRotate(vinylRotationAngle, discSize / 2f, discSize / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(baseDisc, matrix, paint)

        val partial = RemoteViews(context.packageName, R.layout.widget_vinyl_deck)
        partial.setImageViewBitmap(R.id.widget_vinyl_disc, buffer)
        vinylWidgetIds.forEach { widgetId ->
            appWidgetManager.partiallyUpdateAppWidget(widgetId, partial)
        }
    }

    /**
     * Widget A — Napster Dot Player
     * Idle (no track): Completely empty.
     * Active: Full album artwork rendered as a true un-stretched square with 4 circular dots bouncing up and down in bottom-left corner.
     */
    private fun createDotPlayerViews(
        options: Bundle,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        hasTrack: Boolean,
        frame: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dot_player)
        val card = cardSizeFor(options)

        if (hasTrack && albumArt != null) {
            val squareSize = minOf(card.width, card.height).coerceIn(120, 600)
            val cornerRadius = card.cornerPx.coerceAtMost(squareSize * 0.22f)
            val squareCardBitmap = createSquareDotCard(
                albumArt = albumArt,
                targetSize = squareSize,
                cornerPx = cornerRadius,
                isPlaying = isPlaying,
                frame = frame,
            )
            views.setImageViewBitmap(R.id.widget_dot_center_art, squareCardBitmap)
        } else {
            // Completely empty when no music is playing
            views.setImageViewBitmap(R.id.widget_dot_center_art, null)
        }

        // Actions
        views.setOnClickPendingIntent(R.id.widget_dot_action_prev, getPreviousIntent())
        views.setOnClickPendingIntent(R.id.widget_dot_action_play_pause, getPlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_dot_action_next, getNextIntent())
        views.setOnClickPendingIntent(R.id.widget_dot_center_art, getPlayPauseIntent())

        return views
    }

    /**
     * Renders a perfect 1:1 square card for Widget A (Dot Player).
     * Includes album artwork filled with continuous rounded corners and
     * the 4 circular bouncing dots drawn directly in the bottom-left corner.
     */
    private fun createSquareDotCard(
        albumArt: Bitmap?,
        targetSize: Int,
        cornerPx: Float,
        isPlaying: Boolean,
        frame: Int,
    ): Bitmap {
        val size = targetSize.coerceIn(120, 600)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw rounded square card with album art
        val artBitmap = albumArt ?: getRoundedDefaultIcon()
        val scale = maxOf(size.toFloat() / artBitmap.width, size.toFloat() / artBitmap.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (size - artBitmap.width * scale) / 2f,
                (size - artBitmap.height * scale) / 2f,
            )
        }
        val shader = BitmapShader(artBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(matrix)
        }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            isFilterBitmap = true
        }
        drawContinuousRoundedRect(canvas, size.toFloat(), size.toFloat(), cornerPx, cardPaint)

        // 2. Draw 4 circular bouncing dots in the bottom-left corner of the square
        val numDots = 4
        val dotRadius = size * 0.024f
        val dotSpacing = size * 0.052f
        val startX = size * 0.08f
        val baselineY = size - (size * 0.08f)
        val maxBounce = size * 0.055f

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(4f, 0f, 2f, Color.parseColor("#99000000"))
        }

        for (i in 0 until numDots) {
            val cx = startX + i * dotSpacing
            val cy = if (isPlaying) {
                val wave = sin((frame * 0.45 + i * 1.15)).toFloat()
                val bounce = (wave + 1f) / 2f
                baselineY - (bounce * maxBounce)
            } else {
                baselineY
            }
            dotPaint.alpha = if (isPlaying) 255 else 180
            canvas.drawCircle(cx, cy, dotRadius, dotPaint)
        }

        return bitmap
    }

    /**
     * Widget B — Napster Lyrics Player
     * Idle (no track): Completely empty.
     * Active: Left album artwork with red accent edge + 4 bouncing circular dots; Right synchronized lyrics.
     */
    private fun createLyricsPlayerViews(
        options: Bundle,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        hasTrack: Boolean,
        lines: List<LyricsEntry>,
        currentPosition: Long,
        frame: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_lyrics_player)
        val card = cardSizeFor(options)

        views.setInt(R.id.widget_lyrics_root, "setBackgroundResource", if (hasTrack) R.drawable.widget_lyrics_card_bg else 0)

        if (hasTrack) {
            val artPanelWidth = (card.width * 0.44f).toInt().coerceAtLeast(1)
            val artPanelHeight = card.height
            val panelArtBitmap = createArtPanelWithAccent(
                albumArt,
                artPanelWidth,
                artPanelHeight,
                card.cornerPx,
            )
            views.setImageViewBitmap(R.id.widget_lyrics_art, panelArtBitmap)

            val eqBitmap = createDottedEqualiserMotif(isPlaying, frame, hasTrack = true)
            views.setImageViewBitmap(R.id.widget_lyrics_art_eq, eqBitmap)

            if (lines.isEmpty()) {
                views.setTextViewText(R.id.widget_lyrics_active_text, "")
                views.setTextViewText(R.id.widget_lyrics_dots_indicator, "")
            } else {
                val index = LyricsUtils.findCurrentLineIndex(lines, currentPosition)
                    .coerceIn(0, lines.lastIndex)
                views.setTextViewText(R.id.widget_lyrics_active_text, lines[index].text)
                views.setTextViewText(R.id.widget_lyrics_dots_indicator, "•  •  •")
            }
        } else {
            // Completely empty when no music is playing
            views.setImageViewBitmap(R.id.widget_lyrics_art, null)
            views.setImageViewBitmap(R.id.widget_lyrics_art_eq, null)
            views.setTextViewText(R.id.widget_lyrics_active_text, "")
            views.setTextViewText(R.id.widget_lyrics_dots_indicator, "")
            views.setTextViewText(R.id.widget_lyrics_prev_text, "")
        }

        // Actions
        views.setOnClickPendingIntent(R.id.widget_lyrics_action_prev, getPreviousIntent())
        views.setOnClickPendingIntent(R.id.widget_lyrics_action_play_pause, getPlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_lyrics_action_next, getNextIntent())
        views.setOnClickPendingIntent(R.id.widget_lyrics_art, getPlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_lyrics_bg, getOpenAppIntent())

        return views
    }

    /**
     * Widget C — Napster Vinyl Deck
     * Idle (no track): Completely empty.
     * Active: Left vinyl turntable rotating smoothly in circular motion; Right cover art, title, artist, controls.
     */
    private fun createVinylDeckViews(
        options: Bundle,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        circularAlbumArt: Bitmap?,
        isPlaying: Boolean,
        hasTrack: Boolean,
        duration: Long,
        currentPosition: Long,
        rotationAngle: Float,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vinyl_deck)
        val card = cardSizeFor(options)

        views.setInt(R.id.widget_vinyl_root, "setBackgroundResource", if (hasTrack) R.drawable.widget_vinyl_card_bg else 0)

        if (hasTrack) {
            val discSize = (minOf(card.width * 0.48f, card.height.toFloat()) * 0.92f).toInt()
                .coerceAtLeast(60)
                .coerceAtMost(260)
            val vinylBitmap = createVinylRecordDisc(circularAlbumArt, discSize, rotationAngle)
            views.setImageViewBitmap(R.id.widget_vinyl_disc, vinylBitmap)

            val remainingMs = (duration - currentPosition).coerceAtLeast(0)
            val minutes = (remainingMs / 1000) / 60
            val seconds = (remainingMs / 1000) % 60
            val timeStr = if (duration > 0) String.format(Locale.getDefault(), "-%d:%02d", minutes, seconds) else ""
            views.setTextViewText(R.id.widget_vinyl_time_text, timeStr)
            views.setTextViewText(R.id.widget_vinyl_badge, "A-B")

            val thumbArt = albumArt?.let { getRoundedCornerBitmap(it, 0.16f) }
            views.setImageViewBitmap(R.id.widget_vinyl_album_art, thumbArt)
            views.setTextViewText(R.id.widget_vinyl_title, title)
            views.setTextViewText(R.id.widget_vinyl_artist, artist)

            val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.widget_vinyl_play_pause, playPauseIcon)

            views.setViewVisibility(R.id.widget_vinyl_controls, View.VISIBLE)
            views.setViewVisibility(R.id.widget_vinyl_open_app, View.VISIBLE)
        } else {
            // Completely empty when no music is playing
            views.setImageViewBitmap(R.id.widget_vinyl_disc, null)
            views.setImageViewBitmap(R.id.widget_vinyl_album_art, null)
            views.setTextViewText(R.id.widget_vinyl_time_text, "")
            views.setTextViewText(R.id.widget_vinyl_badge, "")
            views.setTextViewText(R.id.widget_vinyl_title, "")
            views.setTextViewText(R.id.widget_vinyl_artist, "")
            views.setViewVisibility(R.id.widget_vinyl_controls, View.GONE)
            views.setViewVisibility(R.id.widget_vinyl_open_app, View.GONE)
        }

        // Actions
        views.setOnClickPendingIntent(R.id.widget_vinyl_prev, getTurntablePreviousIntent())
        views.setOnClickPendingIntent(R.id.widget_vinyl_play_pause, getTurntablePlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_vinyl_next, getTurntableNextIntent())
        views.setOnClickPendingIntent(R.id.widget_vinyl_disc, getTurntablePlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_vinyl_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_vinyl_open_app, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_vinyl_bg, getOpenAppIntent())

        return views
    }

    /**
     * Generates or retrieves the cached unrotated base vinyl record disc.
     */
    private fun getOrCreateBaseDisc(centerArt: Bitmap?, size: Int): Bitmap {
        if (cachedBaseDisc != null && cachedDiscArt === centerArt && cachedDiscSize == size) {
            return cachedBaseDisc!!
        }

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val center = size / 2f
        val radius = size / 2f - 2f

        // Outer glossy vinyl base
        val vinylPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#101012")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, vinylPaint)

        // Concentric vinyl groove rings
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.argb(32, 255, 255, 255)
        }
        val grooveRadii = floatArrayOf(0.92f, 0.86f, 0.80f, 0.74f, 0.68f, 0.62f, 0.56f)
        for (f in grooveRadii) {
            canvas.drawCircle(center, center, radius * f, groovePaint)
        }

        // Center label with album artwork (or Napster accent when art unavailable)
        val labelRadius = radius * 0.42f
        if (centerArt != null) {
            val labelBitmap = Bitmap.createScaledBitmap(centerArt, (labelRadius * 2).toInt(), (labelRadius * 2).toInt(), true)
            val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val saveCount = canvas.save()
            val clipPath = android.graphics.Path().apply {
                addCircle(center, center, labelRadius, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawBitmap(labelBitmap, center - labelRadius, center - labelRadius, artPaint)
            canvas.restoreToCount(saveCount)
        } else {
            val defaultLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#18181A")
                style = Paint.Style.FILL
            }
            canvas.drawCircle(center, center, labelRadius, defaultLabelPaint)
            val redRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = NAPSTER_RED
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawCircle(center, center, labelRadius - 2f, redRingPaint)
        }

        // Center spindle hole
        val spindlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#09090A")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius * 0.08f, spindlePaint)

        val spindleRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#444446")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawCircle(center, center, radius * 0.08f, spindleRimPaint)

        cachedDiscArt = centerArt
        cachedBaseDisc = output
        cachedDiscSize = size
        return output
    }

    /**
     * Rotates the pre-rendered base vinyl disc for smooth circular turntable motion.
     */
    private fun createVinylRecordDisc(centerArt: Bitmap?, size: Int, rotationAngle: Float): Bitmap {
        val baseDisc = getOrCreateBaseDisc(centerArt, size)
        val rotated = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(rotated)
        val matrix = Matrix().apply {
            postRotate(rotationAngle, size / 2f, size / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(baseDisc, matrix, paint)
        return rotated
    }

    /**
     * Left art panel for lyrics widget with red accent divider line.
     */
    private fun createArtPanelWithAccent(
        albumArt: Bitmap?,
        width: Int,
        height: Int,
        cornerPx: Float,
    ): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val src = albumArt ?: getRoundedDefaultIcon()
        val scale = maxOf(width.toFloat() / src.width, height.toFloat() / src.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (width - src.width * scale) / 2f,
                (height - src.height * scale) / 2f,
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(matrix)
            }
        }

        drawContinuousRoundedRect(canvas, width.toFloat(), height.toFloat(), cornerPx, paint)

        // Subtle Napster-red accent bar on the right edge of this panel
        val redAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAPSTER_RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawLine(width - 1.5f, 0f, width - 1.5f, height.toFloat(), redAccentPaint)

        return output
    }

    /**
     * 4 circular dots equalizer motif bouncing up and down when music is playing.
     * When paused, 4 circular dots sit at baseline.
     * When no music is playing, returns null (completely empty).
     */
    private fun createDottedEqualiserMotif(isPlaying: Boolean, frame: Int, hasTrack: Boolean): Bitmap? {
        if (!hasTrack) return null
        val width = 72
        val height = 36
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val numDots = 4
        val dotRadius = 4f
        val startX = 10f
        val dotSpacing = 16f
        val baselineY = height - 8f
        val maxBounce = 16f

        for (i in 0 until numDots) {
            val cx = startX + i * dotSpacing
            val cy = if (isPlaying) {
                // Harmonic sine wave: each dot bounces with a distinct phase offset
                val wave = sin((frame * 0.45 + i * 1.15)).toFloat()
                val bounce = (wave + 1f) / 2f
                baselineY - (bounce * maxBounce)
            } else {
                baselineY
            }
            paint.alpha = if (isPlaying) 255 else 180
            canvas.drawCircle(cx, cy, dotRadius, paint)
        }

        return bitmap
    }

    private fun drawContinuousRoundedRect(
        canvas: Canvas,
        width: Float,
        height: Float,
        cornerPx: Float,
        paint: Paint,
    ) {
        val density = Density(context.resources.displayMetrics.density)
        val shape = ContinuousRoundedRectangle(cornerPx)
        val outline = shape.createOutline(
            size = Size(width, height),
            layoutDirection = LayoutDirection.Ltr,
            density = density,
        )
        when (outline) {
            is Outline.Generic -> canvas.drawPath(outline.path.asAndroidPath(), paint)
            is Outline.Rounded -> canvas.drawRoundRect(
                RectF(0f, 0f, width, height),
                cornerPx,
                cornerPx,
                paint,
            )
            is Outline.Rectangle -> canvas.drawRect(RectF(0f, 0f, width, height), paint)
        }
    }

    private fun getRoundedCornerBitmap(src: Bitmap, cornerRadiusRatio: Float): Bitmap {
        val size = minOf(src.width, src.height)
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = size * cornerRadiusRatio
        drawContinuousRoundedRect(canvas, src.width.toFloat(), src.height.toFloat(), radius, paint)
        return output
    }

    private fun getCircularBitmap(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                val dx = (size - src.width) / 2f
                val dy = (size - src.height) / 2f
                setLocalMatrix(Matrix().apply { postTranslate(dx, dy) })
            }
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return output
    }

    private fun getRoundedDefaultIcon(cornerRadiusRatio: Float = 0.22f): Bitmap {
        val size = 200
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#141A29")
            style = Paint.Style.FILL
        }
        val radius = size * cornerRadiusRatio
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, bgPaint)
        return output
    }

    private suspend fun loadAlbumArt(uri: String, targetSize: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(targetSize)
                    .allowHardware(false)
                    .build()
                imageLoader.execute(request).image?.toBitmap()
            }.getOrNull()
        }

    private data class CardSize(val width: Int, val height: Int, val cornerPx: Float)

    private fun cardSizeFor(options: Bundle): CardSize {
        val density = context.resources.displayMetrics.density
        val portrait = context.resources.configuration.orientation ==
            Configuration.ORIENTATION_PORTRAIT
        val widthDp = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
            else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
        )
        val heightDp = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
        )
        val width = ((if (widthDp > 0) widthDp else 250) * density).toInt().coerceIn(1, 1600)
        val height = ((if (heightDp > 0) heightDp else 110) * density).toInt().coerceIn(1, 1600)

        val radiusDp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) /
                    density
            }.getOrDefault(CARD_CORNER_DP)
        } else {
            CARD_CORNER_DP
        }
        return CardSize(width, height, radiusDp.coerceIn(8f, 28f) * density)
    }

    private suspend fun loadLyricLines(mediaId: String?): List<LyricsEntry> {
        if (mediaId == null) return emptyList()
        val raw = runCatching { database.lyrics(mediaId).first()?.lyrics }.getOrNull()
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { LyricsUtils.parseLyrics(raw) }.getOrDefault(emptyList())
    }

    private fun getOpenAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPlayPauseIntent(): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_PLAY_PAUSE
        }
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNextIntent(): PendingIntent = broadcastToMusicWidget(
        MusicWidgetReceiver.ACTION_NEXT,
        requestCode = 5,
    )

    private fun getPreviousIntent(): PendingIntent = broadcastToMusicWidget(
        MusicWidgetReceiver.ACTION_PREVIOUS,
        requestCode = 6,
    )

    private fun broadcastToMusicWidget(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun getTurntablePlayPauseIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_PLAY_PAUSE
        }
        return PendingIntent.getBroadcast(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntableNextIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_NEXT
        }
        return PendingIntent.getBroadcast(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntablePreviousIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_PREVIOUS
        }
        return PendingIntent.getBroadcast(
            context,
            5,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        val NAPSTER_RED = Color.parseColor("#E53935")
        const val CARD_CORNER_DP = 16f
    }
}
