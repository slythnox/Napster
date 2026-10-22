package com.beatwave.music.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class FlacTaggerTest {

    private fun createMinimalFlac(dummyAudioPayload: ByteArray = ByteArray(128) { (it * 7 % 256).toByte() }): ByteArray {
        val out = ByteArrayOutputStream()
        // Magic "fLaC"
        out.write("fLaC".toByteArray(Charsets.US_ASCII))

        // STREAMINFO block header (type 0, isLast = 1 (0x80), length 34 = 0x000022)
        out.write(0x80) // isLast=true, type=0
        out.write(0x00)
        out.write(0x00)
        out.write(0x22) // 34 bytes

        // STREAMINFO body (34 dummy bytes)
        val streamInfo = ByteArray(34)
        // Set sample rate 44100 (0x0AC44) and 16-bit
        streamInfo[10] = 0x0A.toByte()
        streamInfo[11] = 0xC4.toByte()
        streamInfo[12] = 0x42.toByte()
        streamInfo[13] = 0xF0.toByte()
        out.write(streamInfo)

        // Raw audio frame bytes
        out.write(dummyAudioPayload)

        return out.toByteArray()
    }

    @Test
    fun testRejectsNonFlacInputWithoutModifyingBytes() {
        val mp3Data = "ID3\u0003\u0000\u0000\u0000\u0000\u0000\u0001dummy_mp3_payload_data".toByteArray()
        val taggedMp3 = FlacTagger.tag(
            bytes = mp3Data,
            title = "MP3 Title",
            artist = "MP3 Artist",
            album = "MP3 Album",
            lyrics = null,
            cover = null,
        )
        assertArrayEquals("Non-FLAC data must be returned unchanged", mp3Data, taggedMp3)

        val opusData = "OpusHead\u0001\u0002\u0000\u0000".toByteArray()
        val taggedOpus = FlacTagger.tag(
            bytes = opusData,
            title = "Opus Title",
            artist = "Opus Artist",
            album = null,
            lyrics = null,
            cover = null,
        )
        assertArrayEquals("Opus data must be returned unchanged", opusData, taggedOpus)
    }

    @Test
    fun testUnicodeMetadataPreservedInVorbisComment() {
        val originalAudio = ByteArray(256) { (it % 256).toByte() }
        val flac = createMinimalFlac(originalAudio)

        val unicodeTitle = "First Love (初恋) 🎵"
        val unicodeArtist = "宇多田ヒカル / Édith Piaf / Чайковский"
        val unicodeAlbum = "Utada Hikaru Single Collection Vol. 1"
        val syncedLyrics = "[00:15.20] 最後のキスは [00:18.50] タバコの flavor がした"

        val tagged = FlacTagger.tag(
            bytes = flac,
            title = unicodeTitle,
            artist = unicodeArtist,
            album = unicodeAlbum,
            lyrics = syncedLyrics,
            cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()), // JPEG magic bytes
            coverMime = "image/jpeg",
        )

        val taggedString = String(tagged, Charsets.UTF_8)
        assertTrue("Must contain UTF-8 Japanese title", taggedString.contains("TITLE=$unicodeTitle"))
        assertTrue("Must contain UTF-8 Multilingual artist", taggedString.contains("ARTIST=$unicodeArtist"))
        assertTrue("Must contain UTF-8 Album", taggedString.contains("ALBUM=$unicodeAlbum"))
        assertTrue("Must contain UTF-8 Synced lyrics", taggedString.contains("LYRICS=$syncedLyrics"))

        // Verify audio payload untouched
        val audioTail = tagged.copyOfRange(tagged.size - originalAudio.size, tagged.size)
        assertArrayEquals("Audio frames must be byte-for-byte identical", originalAudio, audioTail)
    }

    @Test
    fun testReplacingExistingTagsDoesNotDuplicateBlocksOrBloat() {
        val originalAudio = ByteArray(64) { 0x42 }
        val flac = createMinimalFlac(originalAudio)

        // Pass 1: Tag with initial metadata
        val taggedPass1 = FlacTagger.tag(
            bytes = flac,
            title = "Initial Title",
            artist = "Initial Artist",
            album = "Initial Album",
            lyrics = "[00:01.00] Line 1",
            cover = byteArrayOf(1, 2, 3, 4),
        )

        // Pass 2: Re-tag the output of Pass 1 with updated metadata
        val taggedPass2 = FlacTagger.tag(
            bytes = taggedPass1,
            title = "Updated Title",
            artist = "Updated Artist",
            album = "Updated Album",
            lyrics = "[00:02.00] Line 2",
            cover = byteArrayOf(5, 6, 7, 8),
        )

        val pass2String = String(taggedPass2, Charsets.UTF_8)

        // Must contain new metadata
        assertTrue(pass2String.contains("TITLE=Updated Title"))
        assertTrue(pass2String.contains("ARTIST=Updated Artist"))
        assertTrue(pass2String.contains("LYRICS=[00:02.00] Line 2"))

        // Old metadata must NOT exist (no duplication or bloat)
        assertFalse("Old title should have been replaced", pass2String.contains("TITLE=Initial Title"))
        assertFalse("Old artist should have been replaced", pass2String.contains("ARTIST=Initial Artist"))
        assertFalse("Old lyrics should have been replaced", pass2String.contains("LYRICS=[00:01.00] Line 1"))

        // Audio frames must still be byte-identical
        val audioTail = taggedPass2.copyOfRange(taggedPass2.size - originalAudio.size, taggedPass2.size)
        assertArrayEquals("Audio frames must remain byte-for-byte identical after multiple retags", originalAudio, audioTail)
    }

    @Test
    fun testWordTimedLyricsFieldsEmbedded() {
        val flac = createMinimalFlac()
        val wordTimingJson = """[{"time":1200,"duration":350,"text":"Hello"}]"""

        val tagged = FlacTagger.tag(
            bytes = flac,
            title = "Song",
            artist = "Artist",
            album = null,
            lyrics = "Hello",
            cover = null,
            wordLyrics = wordTimingJson,
        )

        val taggedString = String(tagged, Charsets.UTF_8)
        assertTrue(taggedString.contains("${FlacTagger.WORD_LYRICS_FIELD}=$wordTimingJson"))
        assertTrue(taggedString.contains("${FlacTagger.COMPAT_WORD_LYRICS_FIELD}=$wordTimingJson"))
    }

    @Test
    fun testSafeFailureOnTruncatedOrMalformedFlac() {
        // 1. Completely empty array
        val empty = ByteArray(0)
        assertArrayEquals(empty, FlacTagger.tag(empty, "Title", "Artist", null, null, null))

        // 2. Just the magic "fLaC" but truncated block header
        val truncatedHeader = "fLaC\u0000".toByteArray()
        assertArrayEquals(truncatedHeader, FlacTagger.tag(truncatedHeader, "Title", "Artist", null, null, null))

        // 3. Valid magic, but block declared size exceeds byte array length
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.US_ASCII))
        out.write(0x80) // isLast=true, type=0
        out.write(0x00)
        out.write(0x01)
        out.write(0x00) // Claims 256 bytes block length
        out.write(ByteArray(10)) // But only supplies 10 bytes
        val corruptedFlac = out.toByteArray()

        val taggedCorrupt = FlacTagger.tag(corruptedFlac, "Title", "Artist", null, null, null)
        assertArrayEquals("Corrupt/truncated FLAC must safely return original bytes without crashing", corruptedFlac, taggedCorrupt)

        // 4. First block is NOT STREAMINFO (violates FLAC spec)
        val nonStreamInfoFirst = ByteArrayOutputStream()
        nonStreamInfoFirst.write("fLaC".toByteArray(Charsets.US_ASCII))
        nonStreamInfoFirst.write(0x84) // isLast=true, type=4 (Vorbis comment instead of STREAMINFO)
        nonStreamInfoFirst.write(0x00)
        nonStreamInfoFirst.write(0x00)
        nonStreamInfoFirst.write(0x04)
        nonStreamInfoFirst.write(ByteArray(4))
        val invalidFirstBlock = nonStreamInfoFirst.toByteArray()

        val taggedInvalid = FlacTagger.tag(invalidFirstBlock, "Title", "Artist", null, null, null)
        assertArrayEquals("FLAC without STREAMINFO as first block must be safely rejected", invalidFirstBlock, taggedInvalid)
    }
}
