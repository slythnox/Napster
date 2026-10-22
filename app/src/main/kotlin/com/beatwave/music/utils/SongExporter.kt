/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.ConnectivityManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.getSystemService
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import com.beatwave.music.constants.AudioQuality
import com.beatwave.music.db.MusicDatabase
import com.beatwave.music.db.entities.LyricsEntity
import com.beatwave.music.di.SongExporterEntryPoint
import com.beatwave.music.download.FlacTagger
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer

/**
 * Exports a song to a user-chosen file.
 *
 * Supports exporting genuine lossless FLAC files with embedded Vorbis comment
 * tags, synchronized lyrics (LRC), and album art without re-encoding audio frames.
 *
 * For non-FLAC streams, remuxes to M4A/AAC (or re-encodes Opus to AAC).
 *
 * Bytes come from the offline caches first, so a downloaded or already-streamed
 * track exports instantly and without network.
 */
@UnstableApi
object SongExporter {

    /** Default lossy container/extension. */
    const val EXTENSION = "m4a"
    const val FLAC_EXTENSION = "flac"
    const val MIME_TYPE = "audio/mp4a-latm"
    const val FLAC_MIME_TYPE = "audio/flac"

    private const val TIMEOUT_US = 10_000L
    private const val TARGET_BITRATE = 192_000

    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    /**
     * Checks if a genuine lossless variant is already cached.
     */
    fun isLosslessAvailable(songId: String, playerCache: Cache, downloadCache: Cache): Boolean {
        val flacKey = "$songId#flac"
        for (cache in listOf(downloadCache, playerCache)) {
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(flacKey))
            if (length > 0L && cache.isCached(flacKey, 0L, length)) return true
        }
        return false
    }

    /**
     * @param songId the media id, which is also the cache key.
     * @param destination a SAF uri the caller obtained from the document picker.
     */
    suspend fun export(
        context: Context,
        songId: String,
        destination: Uri,
        playerCache: Cache,
        downloadCache: Cache,
        database: MusicDatabase? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "export").apply { mkdirs() }
        val sourceFile = File(workDir, "src_$songId")
        val muxedFile = File(workDir, "out_$songId.$EXTENSION")
        val taggedFlacFile = File(workDir, "tagged_$songId.$FLAC_EXTENSION")

        try {
            onProgress(0.05f, "Locating audio…")

            val gotBytes = readFromCache(songId, playerCache, downloadCache, sourceFile) ||
                downloadSource(context, songId, sourceFile) { fraction ->
                    onProgress(0.05f + fraction * 0.55f, "Downloading…")
                }

            if (!gotBytes || sourceFile.length() == 0L) {
                return@withContext Result.Failure("Could not read the audio for this song")
            }

            if (isFlacFile(sourceFile)) {
                onProgress(0.70f, "Applying FLAC metadata…")

                val db = database ?: runCatching {
                    EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        SongExporterEntryPoint::class.java
                    ).database()
                }.getOrNull()

                val song = db?.song(songId)?.firstOrNull()
                val lyricsEntity = db?.lyrics(songId)?.firstOrNull()
                val lyricsText = lyricsEntity?.lyrics?.takeIf { it != LyricsEntity.LYRICS_NOT_FOUND }
                val title = song?.song?.title?.ifBlank { "Unknown Title" } ?: "Unknown Title"
                val artist = song?.artists?.joinToString { it.name }?.ifBlank { "Unknown Artist" } ?: "Unknown Artist"
                val album = song?.song?.albumName

                val coverBytes = song?.song?.thumbnailUrl?.let { url ->
                    runCatching {
                        val conn = java.net.URL(url).openConnection()
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        conn.getInputStream().use { it.readBytes() }
                    }.getOrNull()
                }

                val rawBytes = sourceFile.readBytes()
                val taggedBytes = FlacTagger.tag(
                    bytes = rawBytes,
                    title = title,
                    artist = artist,
                    album = album,
                    lyrics = lyricsText,
                    cover = coverBytes,
                    coverMime = "image/jpeg",
                )
                taggedFlacFile.writeBytes(taggedBytes)

                onProgress(0.9f, "Saving…")
                context.contentResolver.openFileDescriptor(destination, "w")?.use { pfd ->
                    writeTruncated(pfd, taggedFlacFile)
                } ?: return@withContext Result.Failure("Could not open the destination file")

                onProgress(1f, "Done")
                Result.Success
            } else {
                onProgress(0.65f, "Preparing file…")
                val converted = writeM4a(sourceFile, muxedFile)
                if (!converted || muxedFile.length() == 0L) {
                    return@withContext Result.Failure("Could not convert this track to $EXTENSION")
                }

                onProgress(0.9f, "Saving…")
                context.contentResolver.openFileDescriptor(destination, "w")?.use { pfd ->
                    writeTruncated(pfd, muxedFile)
                } ?: return@withContext Result.Failure("Could not open the destination file")

                onProgress(1f, "Done")
                Result.Success
            }
        } catch (e: Exception) {
            Timber.e(e, "Export failed for %s", songId)
            Result.Failure(e.message ?: "Export failed")
        } finally {
            sourceFile.delete()
            muxedFile.delete()
            taggedFlacFile.delete()
        }
    }

    private fun isFlacFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        val header = ByteArray(4)
        runCatching {
            file.inputStream().use { it.read(header) }
        }.getOrNull() ?: return false
        return header[0] == 'f'.code.toByte() &&
               header[1] == 'L'.code.toByte() &&
               header[2] == 'a'.code.toByte() &&
               header[3] == 'C'.code.toByte()
    }

    /** Opening with "w" alone leaves trailing bytes when replacing a longer file. */
    private fun writeTruncated(pfd: ParcelFileDescriptor, source: File) {
        java.io.FileOutputStream(pfd.fileDescriptor).use { out ->
            out.channel.truncate(0)
            source.inputStream().use { it.copyTo(out) }
        }
    }

    /**
     * Copies the track out of the offline caches. Returns false when neither
     * cache holds the whole track, leaving the caller to fetch it.
     */
    private fun readFromCache(
        songId: String,
        playerCache: Cache,
        downloadCache: Cache,
        destination: File,
    ): Boolean {
        val keysToTry = listOf("$songId#flac", songId)
        for (key in keysToTry) {
            for (cache in listOf(downloadCache, playerCache)) {
                val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
                if (length <= 0L || !cache.isCached(key, 0L, length)) continue

                val source: DataSource = CacheDataSource.Factory()
                    .setCache(cache)
                    .setCacheWriteDataSinkFactory(null)
                    .createDataSource()

                val copied = runCatching {
                    source.open(
                        DataSpec.Builder()
                            .setUri(Uri.parse("napster://cache/$key"))
                            .setKey(key)
                            .setPosition(0)
                            .setLength(length)
                            .build()
                    )
                    destination.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = source.read(buffer, 0, buffer.size)
                            if (read == androidx.media3.common.C.RESULT_END_OF_INPUT) break
                            out.write(buffer, 0, read)
                        }
                    }
                    true
                }.onFailure {
                    Timber.w(it, "Cache read failed for %s", key)
                    destination.delete()
                }.getOrDefault(false)

                runCatching { source.close() }
                if (copied) return true
            }
        }
        return false
    }

    private suspend fun downloadSource(
        context: Context,
        songId: String,
        destination: File,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val connectivityManager = context.getSystemService<ConnectivityManager>() ?: return false

        val streamUrl = runCatching {
            YTPlayerUtils.playerResponseForPlayback(
                videoId = songId,
                audioQuality = AudioQuality.HIGH,
                connectivityManager = connectivityManager,
                context = context,
                allowLossless = true,
            ).getOrNull()?.streamUrl
        }.getOrNull() ?: runCatching {
            YTPlayerUtils.playerResponseForPlayback(
                videoId = songId,
                audioQuality = AudioQuality.HIGH,
                connectivityManager = connectivityManager,
                context = context,
                allowLossless = false,
            ).getOrNull()?.streamUrl
        }.getOrNull() ?: return false

        return runCatching {
            val connection = java.net.URL(streamUrl).openConnection()
            connection.connect()
            val total = connection.contentLengthLong
            connection.getInputStream().use { input ->
                destination.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            true
        }.onFailure {
            Timber.w(it, "Stream download failed for %s", songId)
            destination.delete()
        }.getOrDefault(false)
    }

    /**
     * Writes [source] into an M4A at [destination]: a straight stream-copy when
     * the source is already AAC, and a decode/re-encode otherwise (YouTube's
     * Opus streams, which MP4 cannot carry).
     */
    private fun writeM4a(source: File, destination: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(source.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false
            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false

            if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                remuxAac(extractor, trackIndex, inputFormat, destination)
            } else {
                transcodeToAac(extractor, trackIndex, inputFormat, destination)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to write M4A")
            destination.delete()
            false
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun remuxAac(
        extractor: MediaExtractor,
        trackIndex: Int,
        format: MediaFormat,
        destination: File,
    ): Boolean {
        val muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        return try {
            val outTrack = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocateDirect(256 * 1024)
            val info = MediaCodec.BufferInfo()

            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break

                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
            true
        } finally {
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun transcodeToAac(
        extractor: MediaExtractor,
        trackIndex: Int,
        inputFormat: MediaFormat,
        destination: File,
    ): Boolean {
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val outputFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }

        val decoder = MediaCodec.createDecoderByType(mime)
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var outTrack = -1

        return try {
            decoder.configure(inputFormat, null, null, 0)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            decoder.start()
            encoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var decoderEos = false
            var encoderEos = false

            while (!encoderEos) {
                if (!inputEos) {
                    val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuffer = decoder.getInputBuffer(inIdx) ?: break
                        val size = extractor.readSampleData(inBuffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                if (!decoderEos) {
                    val outIdx = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outIdx >= 0) {
                        val pcmBuffer = decoder.getOutputBuffer(outIdx)
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        if (pcmBuffer != null && bufferInfo.size > 0) {
                            feedEncoder(encoder, pcmBuffer, bufferInfo, isEos)
                        } else if (isEos) {
                            feedEncoderEos(encoder)
                        }

                        decoder.releaseOutputBuffer(outIdx, false)
                        if (isEos) decoderEos = true
                    }
                }

                var encIdx = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (encIdx >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        encoder.releaseOutputBuffer(encIdx, false)
                        encIdx = encoder.dequeueOutputBuffer(bufferInfo, 0)
                        continue
                    }

                    val encBuffer = encoder.getOutputBuffer(encIdx) ?: break
                    if (bufferInfo.size > 0 && muxerStarted) {
                        muxer.writeSampleData(outTrack, encBuffer, bufferInfo)
                    }

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderEos = true
                        encoder.releaseOutputBuffer(encIdx, false)
                        break
                    }

                    encoder.releaseOutputBuffer(encIdx, false)
                    encIdx = encoder.dequeueOutputBuffer(bufferInfo, 0)
                }

                if (encIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) error("Encoder format changed twice")
                    outTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
            }
            true
        } finally {
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun feedEncoder(
        encoder: MediaCodec,
        source: ByteBuffer,
        info: MediaCodec.BufferInfo,
        isEos: Boolean,
    ) {
        val inIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
        if (inIdx < 0) return
        val target = encoder.getInputBuffer(inIdx) ?: return
        target.clear()
        source.position(info.offset)
        source.limit(info.offset + info.size)
        target.put(source)
        val flags = if (isEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
        encoder.queueInputBuffer(inIdx, 0, info.size, info.presentationTimeUs, flags)
    }

    private fun feedEncoderEos(encoder: MediaCodec) {
        val inIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
        if (inIdx >= 0) {
            encoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
    }

    /** Filesystem-safe "Artist - Title.ext". */
    fun suggestedFileName(title: String, artist: String?, isFlac: Boolean = false): String {
        val base = listOfNotNull(artist?.takeIf { it.isNotBlank() }, title.takeIf { it.isNotBlank() })
            .joinToString(" - ")
            .ifBlank { "audio" }
        val ext = if (isFlac) FLAC_EXTENSION else EXTENSION
        return base.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120) + ".$ext"
    }
}
