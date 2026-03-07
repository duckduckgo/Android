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

package com.duckduckgo.sync.impl.autorecovery

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.persistent.storage.api.PersistentStorage
import com.duckduckgo.persistent.storage.api.PersistentStorageAvailability
import com.duckduckgo.persistent.storage.api.PersistentStorageAvailability.AvailableEncrypted
import com.duckduckgo.persistent.storage.api.PersistentStorageAvailability.AvailableUnencrypted
import com.duckduckgo.persistent.storage.api.PersistentStorageAvailability.BuildTypeUnsupported
import com.duckduckgo.persistent.storage.api.PersistentStorageAvailability.Unavailable
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

/**
 * Observes privacy config download to validate persistent storage integration stability.
 *
 * This is part of de-risking strategy and will be later removed:
 * - Feature flagged capability detection
 * - Fires pixels on success/error to validate Block Store works reliably
 */
@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
class SyncAutoRecoveryCapabilityObserver @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val syncFeature: SyncFeature,
    private val persistentStorage: PersistentStorage,
    private val pixel: Pixel,
) : PrivacyConfigCallbackPlugin {

    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            runCapabilityDetection()
        }
    }

    private suspend fun runCapabilityDetection() {
        // Check feature flags first - don't touch Block Store API if both disabled
        val writeEnabled = syncFeature.syncAutoRecoveryCapabilityDetectionWrite().isEnabled()
        val readEnabled = syncFeature.syncAutoRecoveryCapabilityDetectionRead().isEnabled()

        if (!writeEnabled && !readEnabled) {
            logcat { "Sync-Recovery: both write and read FFs disabled, skipping" }
            return
        }

        logcat { "Sync-Recovery: capability detection enabled (write=$writeEnabled, read=$readEnabled)" }

        // Now check availability (this touches Block Store API)
        val availability = checkAvailability() ?: return

        // For unsupported build types (e.g., F-Droid), exit without any pixels
        if (availability is BuildTypeUnsupported) {
            logcat { "Sync-Recovery: build type does not support persistent storage, skipping" }
            return
        }

        // Fire the availability pixel
        fireAvailabilityPixel(availability)

        if (availability is Unavailable) {
            return
        }

        // Read first to validate previous session's write persisted
        if (readEnabled) {
            read()
        } else {
            logcat { "Sync-Recovery: read FF disabled, skipping read test" }
        }

        // Write new value for next session to read
        if (writeEnabled) {
            write()
        } else {
            logcat { "Sync-Recovery: write FF disabled, skipping write test" }
        }
    }

    /**
     * Returns null if exception occurred (error pixel fired), otherwise returns the availability.
     */
    private suspend fun checkAvailability(): PersistentStorageAvailability? {
        return try {
            persistentStorage.checkAvailability()
        } catch (t: Throwable) {
            currentCoroutineContext().ensureActive()
            logcat(LogPriority.WARN) { "Sync-Recovery: availability check failed - ${t.message}" }
            pixel.fire(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_CHECK_ERROR_DAILY, type = Daily())
            null
        }
    }

    private fun fireAvailabilityPixel(availability: PersistentStorageAvailability) {
        when (availability) {
            is AvailableEncrypted -> {
                logcat { "Sync-Recovery: persistent storage available with E2E encryption" }
                pixel.fire(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_AVAILABLE_ENCRYPTED_DAILY, type = Daily())
            }
            is AvailableUnencrypted -> {
                logcat { "Sync-Recovery: persistent storage available without E2E encryption" }
                pixel.fire(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_AVAILABLE_UNENCRYPTED_DAILY, type = Daily())
            }
            is Unavailable -> {
                logcat { "Sync-Recovery: persistent storage unavailable" }
                pixel.fire(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_UNAVAILABLE_DAILY, type = Daily())
            }
            is BuildTypeUnsupported -> {
                // No pixel for unsupported build type - this code path shouldn't be reached
                // as we return early in runCapabilityDetection()
            }
        }
    }

    private suspend fun write() {
        try {
            logcat { "Sync-Recovery: attempting to write to persistent storage..." }
            val testValue = "${TEST_VALUE_PREFIX}${System.currentTimeMillis()}"

            persistentStorage.write(TEST_KEY, testValue)
                .onSuccess {
                    logcat { "Sync-Recovery: write success" }
                    pixel.fire(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_WRITE_SUCCESS_DAILY, type = Daily())
                }
                .onFailure { exception ->
                    logcat(LogPriority.WARN) { "Sync-Recovery: write failed - ${exception.message}" }
                    pixel.fire(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_WRITE_ERROR_DAILY, type = Daily())
                }
        } catch (t: Throwable) {
            currentCoroutineContext().ensureActive()
            logcat(LogPriority.WARN) { "Sync-Recovery: write failed - ${t.message}" }
            pixel.fire(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_WRITE_ERROR_DAILY, type = Daily())
        }
    }

    private suspend fun read() {
        try {
            logcat { "Sync-Recovery: attempting to read from persistent storage..." }

            persistentStorage.read(TEST_KEY)
                .onSuccess { readValue ->
                    val isValid = readValue == null || readValue.startsWith(TEST_VALUE_PREFIX)
                    if (isValid) {
                        logcat { "Sync-Recovery: read success (value=${if (readValue != null) "${readValue.length} chars" else "null"})" }
                        pixel.fire(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_READ_SUCCESS_DAILY, type = Daily())
                    } else {
                        logcat(LogPriority.WARN) { "Sync-Recovery: read returned unexpected value" }
                        pixel.fire(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_READ_MISMATCH_DAILY, type = Daily())
                    }
                }
                .onFailure { exception ->
                    logcat(LogPriority.WARN) { "Sync-Recovery: read failed - ${exception.message}" }
                    pixel.fire(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_READ_ERROR_DAILY, type = Daily())
                }
        } catch (t: Throwable) {
            currentCoroutineContext().ensureActive()
            logcat(LogPriority.WARN) { "Sync-Recovery: read failed - ${t.message}" }
            pixel.fire(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_READ_ERROR_DAILY, type = Daily())
        }
    }

    companion object {
        private const val TEST_KEY = "com.duckduckgo.sync.derisk.test"
        private const val TEST_VALUE_PREFIX = "derisk_test_"
    }
}
