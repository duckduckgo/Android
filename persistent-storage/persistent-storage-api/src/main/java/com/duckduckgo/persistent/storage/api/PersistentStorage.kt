/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.persistent.storage.api

/**
 * Abstraction for persistent storage that survives app uninstall/reinstall.
 *
 * On Android, this is backed by Google's Block Store API on play/internal builds,
 * with a no-op implementation for F-Droid builds (no Google Play Services).
 */
interface PersistentStorage {
    /**
     * Check if persistent storage is available and what encryption level is supported.
     */
    suspend fun checkAvailability(): PersistentStorageAvailability

    /**
     * Write a value to persistent storage.
     * @param key The key to store the value under
     * @param value The value to write
     * @return Result.success if write succeeded, Result.failure with exception otherwise
     */
    suspend fun write(key: String, value: String): Result<Unit>

    /**
     * Read a value from persistent storage.
     * @param key The key to read
     * @return Result.success with the value (or null if not found), Result.failure with exception otherwise
     */
    suspend fun read(key: String): Result<String?>
}

/**
 * Availability status of persistent storage.
 */
sealed class PersistentStorageAvailability {
    /** Persistent storage is available with end-to-end encryption */
    object AvailableEncrypted : PersistentStorageAvailability()

    /** Persistent storage is available but without end-to-end encryption */
    object AvailableUnencrypted : PersistentStorageAvailability()

    /** Persistent storage is not available at runtime (e.g., no Google Play Services on device) */
    object Unavailable : PersistentStorageAvailability()

    /** Build type does not support persistent storage (e.g., F-Droid has no GMS) */
    object BuildTypeUnsupported : PersistentStorageAvailability()
}
