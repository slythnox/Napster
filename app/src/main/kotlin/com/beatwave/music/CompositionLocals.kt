/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Backward-compatibility aliases for CompositionLocals defined in MainActivity.kt
 * which now lives in com.beatwave.music.app.
 */

package com.beatwave.music

val LocalDatabase get() = com.beatwave.music.app.LocalDatabase
val LocalRingtoneViewModel get() = com.beatwave.music.app.LocalRingtoneViewModel
val LocalPlayerConnection get() = com.beatwave.music.app.LocalPlayerConnection
val LocalPlayerAwareWindowInsets get() = com.beatwave.music.app.LocalPlayerAwareWindowInsets
val LocalDownloadUtil get() = com.beatwave.music.app.LocalDownloadUtil
val LocalSyncUtils get() = com.beatwave.music.app.LocalSyncUtils
val LocalIsPlayerExpanded get() = com.beatwave.music.app.LocalIsPlayerExpanded
val LocalTabView get() = com.beatwave.music.app.LocalTabView
