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

package com.duckduckgo.persistentstorage.api

/**
 * Storage that persists across app (re)installs.
 * Data can be restored after reinstall, or on a new device if the user restored the app from a backup.
 */
interface PersistentStorage {
    /**
     * Checks the availability of persistent storage on this device.
     * @return the level of availability including E2E encryption support.
     */
    suspend fun checkAvailability(): PersistentStorageAvailability

    /**
     * Stores data for the given key.
     * @param key The storage key (from [PersistentStorageKey])
     * @param value The raw bytes to store
     * @return Result.success if stored, Result.failure with exception otherwise
     */
    suspend fun store(key: PersistentStorageKey, value: ByteArray): Result<Unit>

    /**
     * Clears data for the given key.
     * @param key The storage key to clear
     * @return Result.success if cleared, Result.failure with exception otherwise
     */
    suspend fun clear(key: PersistentStorageKey): Result<Unit>

    /**
     * Retrieves data for a given key.
     * @param key The storage key to retrieve
     * @return Result.success with the stored bytes (or null if no data stored), Result.failure with exception otherwise
     */
    suspend fun retrieve(key: PersistentStorageKey): Result<ByteArray?>
}

/**
 * Base class for persistent storage keys. Each module can define its own keys.
 *
 * @property key The actual storage key string, must be unique across all keys
 * @property shouldBackupToCloud Whether this data should be backed up to cloud storage
 */
abstract class PersistentStorageKey(
    val key: String,
    val shouldBackupToCloud: Boolean,
)

/**
 * Describes the availability of persistent storage on this device.
 */
sealed class PersistentStorageAvailability {
    /**
     * Persistent storage is not available (e.g., Google Play Services unavailable or unsupported build).
     */
    data object Unavailable : PersistentStorageAvailability()

    /**
     * Persistent storage is available.
     *
     * @property isEndToEndEncryptionSupported Whether cloud backup is secured with end-to-end encryption. Examples of when it will return false:
     *   - when device doesn't have a screen lock or is
     *   - when device is running OS version below API 28
     *   - when device has Backups disabled at the OS-level
     */
    data class Available(val isEndToEndEncryptionSupported: Boolean) : PersistentStorageAvailability()
}

/**
 * Exception returned inside [Result.failure] when persistent storage is unavailable.
 * To avoid this, call [PersistentStorage.checkAvailability] first.
 */
class PersistentStorageUnavailableException : Exception("Persistent storage is not available")
