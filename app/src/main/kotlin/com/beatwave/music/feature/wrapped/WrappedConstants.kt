/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.feature.wrapped
import java.util.Calendar

object WrappedConstants {
    val YEAR = Calendar.getInstance().get(Calendar.YEAR)
    val PLAYLIST_NAME = "Napster $YEAR"
}
