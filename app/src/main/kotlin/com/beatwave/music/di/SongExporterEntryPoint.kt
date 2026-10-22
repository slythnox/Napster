/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.di

import com.beatwave.music.db.MusicDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SongExporterEntryPoint {
    fun database(): MusicDatabase
}
