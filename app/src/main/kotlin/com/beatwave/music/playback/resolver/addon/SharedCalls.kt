/*
 * Napster (C) 2026
 * Licensed under GPL-3.0
 */

package com.beatwave.music.playback.resolver.addon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Deduplicates in-flight calls and caches recent results for a given TTL.
 */
class SharedCalls<T>(
    private val ttlMs: Long,
    private val scope: CoroutineScope,
    private val logger: ((String) -> Unit)? = null,
) {
    private class Cached<T>(val value: T, val expiresAtMs: Long)

    private val completed = ConcurrentHashMap<String, Cached<T>>()
    private val inFlight = ConcurrentHashMap<String, Deferred<Result<T>>>()
    private val mutex = Mutex()

    suspend fun get(
        key: String,
        describe: (() -> String)? = null,
        loader: suspend () -> Result<T>,
    ): Result<T> {
        val now = System.currentTimeMillis()
        val cached = completed[key]
        if (cached != null) {
            if (now < cached.expiresAtMs) {
                return Result.success(cached.value)
            } else {
                completed.remove(key, cached)
            }
        }

        val deferred: Deferred<Result<T>> = mutex.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                existing
            } else {
                val call = scope.async {
                    describe?.let { logger?.invoke(it()) }
                    val result = loader()
                    result.onSuccess { value ->
                        if (ttlMs > 0) {
                            completed[key] = Cached(value, System.currentTimeMillis() + ttlMs)
                        }
                    }
                    mutex.withLock { inFlight.remove(key) }
                    result
                }
                inFlight[key] = call
                call
            }
        }
        return deferred.await()
    }

    fun clear() {
        completed.clear()
        inFlight.clear()
    }

    fun clearCompleted() {
        completed.clear()
    }
}
