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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.persistent.storage.api.PersistentStorage
import com.duckduckgo.persistent.storage.api.PersistentStorageAvailability
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
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val persistentStorage: PersistentStorage = mock()
    private val pixel: Pixel = mock()

    private lateinit var testee: SyncAutoRecoveryCapabilityObserver

    @Before
    fun setup() {
        testee = SyncAutoRecoveryCapabilityObserver(
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            syncFeature = syncFeature,
            persistentStorage = persistentStorage,
            pixel = pixel,
        )
    }

    @Test
    fun whenBuildTypeUnsupportedThenSkipsEverythingAndFiresNoPixels() = runTest {
        configureAvailability(PersistentStorageAvailability.BuildTypeUnsupported)
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(true)

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(persistentStorage).checkAvailability()
        verify(persistentStorage, never()).write(any(), any())
        verify(persistentStorage, never()).read(any())
        verifyNoInteractions(pixel)
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
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureWriteSuccess()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(persistentStorage).checkAvailability()
        verify(persistentStorage).write(any(), any())
        verify(persistentStorage, never()).read(any())
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
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureReadSuccess("derisk_test_123")

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(persistentStorage).checkAvailability()
        verify(persistentStorage, never()).write(any(), any())
        verify(persistentStorage).read(any())
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
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureWriteSuccess()
        configureReadSuccess()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(persistentStorage).checkAvailability()
        verify(persistentStorage).write(any(), any())
        verify(persistentStorage).read(any())
    }

    @Test
    fun whenBlockStoreAvailableEncryptedThenFiresEncryptedPixel() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(false)
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureWriteSuccess()

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
        configureAvailability(PersistentStorageAvailability.AvailableUnencrypted)
        configureWriteSuccess()

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
        verify(persistentStorage, never()).write(any(), any())
        verify(persistentStorage, never()).read(any())
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
        verify(persistentStorage, never()).write(any(), any())
        verify(persistentStorage, never()).read(any())
    }

    @Test
    fun whenWriteSucceedsThenFiresWriteSuccessPixel() = runTest {
        configureWriteFlagEnabled(true)
        configureReadFlagEnabled(false)
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureWriteSuccess()

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
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureWriteFailure()

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
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureWriteThrows()

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
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureWriteSuccess()
        configureReadSuccess("unexpected_value") // Value without expected prefix

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
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureReadSuccess()

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
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureReadSuccess("derisk_test_1234567890")

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
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureReadFailure()

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
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureReadThrows()

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
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureWriteSuccess()
        configureReadFailure()

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
        configureAvailability(PersistentStorageAvailability.AvailableEncrypted)
        configureReadFailure()
        configureWriteSuccess()

        testee.onPrivacyConfigDownloaded()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(persistentStorage).read(any())
        verify(persistentStorage).write(any(), any())
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

    private fun configureWriteSuccess() = runBlocking {
        whenever(persistentStorage.write(any(), any())).thenReturn(Result.success(Unit))
    }

    private fun configureWriteFailure() = runBlocking {
        whenever(persistentStorage.write(any(), any())).thenReturn(Result.failure(RuntimeException("Write failed")))
    }

    private fun configureWriteThrows() = runBlocking {
        whenever(persistentStorage.write(any(), any())).thenThrow(RuntimeException("Write failed"))
    }

    private fun configureReadSuccess(value: String? = null) = runBlocking {
        whenever(persistentStorage.read(any())).thenReturn(Result.success(value))
    }

    private fun configureReadFailure() = runBlocking {
        whenever(persistentStorage.read(any())).thenReturn(Result.failure(RuntimeException("Read failed")))
    }

    private fun configureReadThrows() = runBlocking {
        whenever(persistentStorage.read(any())).thenThrow(RuntimeException("Read failed"))
    }
}
