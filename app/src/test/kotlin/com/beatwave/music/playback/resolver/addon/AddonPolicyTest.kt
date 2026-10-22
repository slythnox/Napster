package com.beatwave.music.playback.resolver.addon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonPolicyTest {

    private fun manifest(extra: String): AddonManifest =
        AddonJson.decodeFromString("""{"id":"test","name":"Test Addon","resources":["search","stream"]$extra}""")

    @Test
    fun testMissingKeysDefaultToAllowedDownloadsAndNoLosslessGate() {
        val m = manifest("")
        assertTrue(m.downloadsAllowed)
        assertFalse(m.requiresLosslessOutput)
    }

    @Test
    fun testBooleansAndStringsCoercedCorrectly() {
        for (off in listOf("false", "\"false\"", "0", "\"0\"")) {
            val m = manifest(""", "allowDownloads": $off""")
            assertFalse("Expected false for $off", m.downloadsAllowed)
        }

        for (on in listOf("true", "\"true\"", "1", "\"1\"")) {
            val m = manifest(""", "checkValidLossless": $on""")
            assertTrue("Expected true for $on", m.requiresLosslessOutput)
        }
    }

    @Test
    fun testFallbackOnUnrecognizedValue() {
        val m = manifest(""", "allowDownloads": "invalid_value", "checkValidLossless": null""")
        assertTrue(m.downloadsAllowed)
        assertFalse(m.requiresLosslessOutput)
    }
}
