package com.beatwave.music.feature.youtubeimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubePlaylistImporterTest {

    private val importer = YouTubePlaylistImporter(database = null)

    @Test
    fun extractPlaylistId_fromMusicYoutubeUrl() {
        val url = "https://music.youtube.com/playlist?list=PL1234567890abcdef"
        assertEquals("PL1234567890abcdef", importer.extractPlaylistId(url))
    }

    @Test
    fun extractPlaylistId_fromYoutubeUrlWithQueryParams() {
        val url = "https://www.youtube.com/playlist?list=PLxyz987654321&si=abcdef123&feature=shared"
        assertEquals("PLxyz987654321", importer.extractPlaylistId(url))
    }

    @Test
    fun extractPlaylistId_withVlPrefix() {
        val url = "https://music.youtube.com/playlist?list=VLPL1234567890abcdef"
        assertEquals("PL1234567890abcdef", importer.extractPlaylistId(url))
    }

    @Test
    fun extractPlaylistId_fromRawPlaylistId() {
        val raw = "PL1234567890abcdef"
        assertEquals("PL1234567890abcdef", importer.extractPlaylistId(raw))
    }

    @Test
    fun extractPlaylistId_fromOlAkId() {
        val raw = "OLAK5uy_k1234567890abcdef"
        assertEquals("OLAK5uy_k1234567890abcdef", importer.extractPlaylistId(raw))
    }

    @Test
    fun extractPlaylistId_invalidReturnsNull() {
        assertNull(importer.extractPlaylistId(""))
        assertNull(importer.extractPlaylistId("   "))
        assertNull(importer.extractPlaylistId("invalid url without list"))
        assertNull(importer.extractPlaylistId("abc"))
    }
}
