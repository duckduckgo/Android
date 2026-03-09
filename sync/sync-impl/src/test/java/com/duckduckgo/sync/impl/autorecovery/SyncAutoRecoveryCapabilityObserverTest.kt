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

import android.annotation.SuppressLint
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.persistentstorage.api.PersistentStorage
import com.duckduckgo.persistentstorage.api.PersistentStorageAvailability
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class SyncAutoRecoveryCapabilityObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val appBuildConfig: AppBuildConfig = mock {
        on { flavor }.thenReturn(BuildFlavor.PLAY)
    }
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val persistentStorage: PersistentStorage = mock()
    private val pixel: Pixel = mock()

    private lateinit var testee: SyncAutoRecoveryCapabilityObserver

    @Before
    fun setup() {
        testee = SyncAutoRecoveryCapabilityObserver(
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appBuildConfig = appBuildConfig,
            syncFeature = syncFeature,
            persistentStorage = persistentStorage,
            pixel = pixel,
        )
    }

    @Test
    fun whenBothFlagsDisabledThenSkipsEverythingAndFiresNoPixels() = runTest {
        configureWriteFlagEnabled(false)
        configureReadFlagEnabled(false)

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(persistentStorage)
        verifyNoInteractions(pixel)
    }

    @Test
    fun whenWriteFlagEnabledAndReadDisabledThenChecksAvailabilityAndWritesButSkipsRead() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(false)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureStoreSuccess()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(persistentStorage).checkAvailability()
        verify(persistentStorage).store(any(), any())
        verify(persistentStorage, never()).retrieve(any())
        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_AVAILABLE_ENCRYPTED_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_WRITE_SUCCESS_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenReadFlagEnabledAndWriteDisabledThenChecksAvailabilityAndReadsButSkipsWrite() = runTest {
        configureWriteFlagEnabled(false)
        configureReadFlagEnabled(true)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureRetrieveSuccess("derisk_test_123")

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(persistentStorage).checkAvailability()
        verify(persistentStorage, never()).store(any(), any())
        verify(persistentStorage).retrieve(any())
        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_AVAILABLE_ENCRYPTED_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_READ_SUCCESS_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenBothFlagsEnabledThenExecutesFullFlow() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(true)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureStoreSuccess()
        configureRetrieveSuccess()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(persistentStorage).checkAvailability()
        verify(persistentStorage).store(any(), any())
        verify(persistentStorage).retrieve(any())
    }

    @Test
    fun whenBlockStoreAvailableEncryptedThenFiresEncryptedPixel() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(false)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureStoreSuccess()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_AVAILABLE_ENCRYPTED_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenBlockStoreAvailableUnencryptedThenFiresUnencryptedPixel() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(false)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = false))
        configureStoreSuccess()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_AVAILABLE_UNENCRYPTED_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenBlockStoreUnavailableThenFiresUnavailablePixelAndSkipsWriteAndRead() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(true)
        configureAvailability(PersistentStorageAvailability.Unavailable)

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_UNAVAILABLE_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
        verify(persistentStorage, never()).store(any(), any())
        verify(persistentStorage, never()).retrieve(any())
    }

    @Test
    fun whenAvailabilityCheckThrowsExceptionThenFiresCheckErrorPixelAndSkipsWriteAndRead() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(true)
        configureAvailabilityThrows()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_CHECK_ERROR_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
        verify(persistentStorage, never()).store(any(), any())
        verify(persistentStorage, never()).retrieve(any())
    }

    @Test
    fun whenWriteSucceedsThenFiresWriteSuccessPixel() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(false)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureStoreSuccess()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_WRITE_SUCCESS_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenWriteFailsThenFiresWriteErrorPixel() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(false)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureStoreFailure()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_WRITE_ERROR_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenWriteThrowsExceptionThenFiresWriteErrorPixel() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(false)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureStoreThrows()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_WRITE_ERROR_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenReadSucceedsWithInvalidPrefixThenFiresReadMismatchPixel() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(true)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureStoreSuccess()
        configureRetrieveSuccess("unexpected_value") // Value without expected prefix

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_READ_MISMATCH_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenReadSucceedsWithNullValueAndNoWriteThenFiresReadSuccessPixel() = runTest {
        configureWriteFlagEnabled(false)
        configureReadFlagEnabled(true)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureRetrieveSuccess()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_READ_SUCCESS_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenReadSucceedsWithValidPrefixThenFiresReadSuccessPixel() = runTest {
        configureWriteFlagEnabled(false)
        configureReadFlagEnabled(true)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureRetrieveSuccess("derisk_test_1234567890")

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_READ_SUCCESS_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenReadFailsThenFiresReadErrorPixel() = runTest {
        configureWriteFlagEnabled(false)
        configureReadFlagEnabled(true)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureRetrieveFailure()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_READ_ERROR_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenReadThrowsExceptionThenFiresReadErrorPixel() = runTest {
        configureWriteFlagEnabled(false)
        configureReadFlagEnabled(true)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureRetrieveThrows()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_READ_ERROR_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenWriteSucceedsButReadFailsThenFiresBothWriteSuccessAndReadErrorPixels() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(true)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureStoreSuccess()
        configureRetrieveFailure()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_WRITE_SUCCESS_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_READ_ERROR_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenReadFailsThenWriteStillAttemptedIfWriteFlagEnabled() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(true)
        configureAvailability(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))
        configureRetrieveFailure()
        configureStoreSuccess()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(persistentStorage).retrieve(any())
        verify(persistentStorage).store(any(), any())
        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_READ_ERROR_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
        verify(pixel).fire(
            eq(SyncPixelName.SYNC_AUTO_RECOVERY_BLOCKSTORE_WRITE_SUCCESS_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    private fun configureWriteFlagEnabled(enabled: Boolean) {
        syncFeature.syncAutoRecoveryCapabilityDetectionWrite().setRawStoredState(State(enable = enabled))
    }

    private fun configureReadFlagEnabled(enabled: Boolean) {
        syncFeature.syncAutoRecoveryCapabilityDetectionRead().setRawStoredState(State(enable = enabled))
    }

    private fun configureAvailability(availability: PersistentStorageAvailability) = runBlocking {
        whenever(persistentStorage.checkAvailability()).thenReturn(availability)
    }

    private fun configureAvailabilityThrows() = runBlocking {
        whenever(persistentStorage.checkAvailability()).thenThrow(RuntimeException("Availability check failed"))
    }

    private fun configureStoreSuccess() = runBlocking {
        whenever(persistentStorage.store(any(), any())).thenReturn(Result.success(Unit))
    }

    private fun configureStoreFailure() = runBlocking {
        whenever(persistentStorage.store(any(), any())).thenReturn(Result.failure(RuntimeException("Store failed")))
    }

    private fun configureStoreThrows() = runBlocking {
        whenever(persistentStorage.store(any(), any())).thenThrow(RuntimeException("Store failed"))
    }

    private fun configureRetrieveSuccess(value: String? = null) = runBlocking {
        val bytes = value?.toByteArray(Charsets.UTF_8)
        whenever(persistentStorage.retrieve(any())).thenReturn(Result.success(bytes))
    }

    private fun configureRetrieveFailure() = runBlocking {
        whenever(persistentStorage.retrieve(any())).thenReturn(Result.failure(RuntimeException("Retrieve failed")))
    }

    private fun configureRetrieveThrows() = runBlocking {
        whenever(persistentStorage.retrieve(any())).thenThrow(RuntimeException("Retrieve failed"))
    }
}
