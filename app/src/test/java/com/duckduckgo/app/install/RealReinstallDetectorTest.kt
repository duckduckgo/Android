/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.install

import android.content.SharedPreferences
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.persistentstorage.api.PersistentStorage
import com.duckduckgo.persistentstorage.api.PersistentStorageAvailability
import com.duckduckgo.persistentstorage.api.PersistentStorageKey
import com.duckduckgo.persistentstorage.api.PersistentStorageUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RealReinstallDetectorTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val dispatcherProvider = DefaultDispatcherProvider()
    private val persistentStorage = FakePersistentStorage()
    private val pixel: Pixel = mock()
    private val ddgDownloadsDirectory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "DuckDuckGo",
    )

    @Before
    fun setUp() {
        ddgDownloadsDirectory.deleteRecursively()
    }

    @After
    fun tearDown() {
        ddgDownloadsDirectory.deleteRecursively()
    }

    @Test
    @Config(sdk = [34])
    fun whenDdgDownloadsDirectoryIsMissingThenIsNotReinstallAndDirectoryIsCreated() = runTest {
        val preferences = InMemorySharedPreferences()

        assertFalse(reinstallDetectorWith(preferences).isAppReinstall())
        assertTrue(ddgDownloadsDirectory.exists())
        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
    }

    @Test
    @Config(sdk = [34])
    fun whenDdgDownloadsDirectoryExistsThenIsReinstall() = runTest {
        ddgDownloadsDirectory.mkdirs()
        val preferences = InMemorySharedPreferences()

        assertTrue(reinstallDetectorWith(preferences).isAppReinstall())
        assertTrue(preferences.getBoolean(APP_REINSTALLED_KEY, false))
    }

    @Test
    @Config(sdk = [34])
    fun whenAnswerIsAlreadySavedThenItIsReusedWithoutCheckingTheDirectory() = runTest {
        val preferences = InMemorySharedPreferences().apply { putBoolean(APP_REINSTALLED_KEY, true) }

        assertTrue(reinstallDetectorWith(preferences).isAppReinstall())
        assertFalse(ddgDownloadsDirectory.exists())
    }

    @Test
    @Config(sdk = [29])
    fun whenAndroidVersionIsBelow11ThenDownloadsDirectoryIsNotUsed() = runTest {
        ddgDownloadsDirectory.mkdirs()

        assertFalse(reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall())
    }

    @Test
    @Config(sdk = [34])
    fun whenAndroidVersionIs11OrAboveThenBlockStoreIsNotUsed() = runTest {
        persistentStorage.values[InstallSeenPersistentStorageKey.key] = byteArrayOf(1)

        assertFalse(reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall())
        assertEquals(0, persistentStorage.retrieveCalls)
        assertEquals(0, persistentStorage.storeCalls)
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndInstallSeenFlagExistsThenIsReinstall() = runTest {
        persistentStorage.values[InstallSeenPersistentStorageKey.key] = byteArrayOf(1)
        val preferences = InMemorySharedPreferences()

        assertTrue(reinstallDetectorWith(preferences).isAppReinstall())
        assertTrue(preferences.getBoolean(APP_REINSTALLED_KEY, false))
        assertTrue(preferences.getBoolean(INSTALL_SEEN_FLAG_STORED_KEY, false))
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndInstallSeenFlagIsMissingThenIsNotReinstallAndFlagIsStored() = runTest {
        val preferences = InMemorySharedPreferences()
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        assertFalse(reinstallDetector.isAppReinstall())
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        assertNotNull(persistentStorage.values[InstallSeenPersistentStorageKey.key])
        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
        assertTrue(preferences.getBoolean(INSTALL_SEEN_FLAG_STORED_KEY, false))
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndBlockStoreIsUnavailableThenIsNotReinstallAndAnswerIsSaved() = runTest {
        persistentStorage.available = false
        val preferences = InMemorySharedPreferences()

        assertFalse(reinstallDetectorWith(preferences).isAppReinstall())
        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
        assertFalse(preferences.getBoolean(INSTALL_SEEN_FLAG_STORED_KEY, false))
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndBlockStoreNeverAnswersThenIsNotReinstallAfterTheTimeLimit() = runTest {
        persistentStorage.neverAnswer = true
        val preferences = InMemorySharedPreferences()
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        val result = coroutineTestRule.testScope.async { reinstallDetector.isAppReinstall() }
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertFalse(result.await())
        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndStoringTheFlagNeverFinishesThenCallersDoNotWaitForIt() = runTest {
        persistentStorage.storeNeverAnswers = true
        val preferences = InMemorySharedPreferences()
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        val first = coroutineTestRule.testScope.async { reinstallDetector.isAppReinstall() }
        val second = coroutineTestRule.testScope.async { reinstallDetector.isAppReinstall() }

        assertTrue(first.isCompleted)
        assertTrue(second.isCompleted)
        assertFalse(first.await())
        assertFalse(second.await())
        assertEquals(1, persistentStorage.storeCalls)
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndStoringTheFlagFailedThenAppStartStoresIt() = runTest {
        persistentStorage.failStore = true
        val preferences = InMemorySharedPreferences()
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )
        assertFalse(reinstallDetector.isAppReinstall())
        assertFalse(preferences.getBoolean(INSTALL_SEEN_FLAG_STORED_KEY, false))

        persistentStorage.failStore = false
        reinstallDetector.onCreate(mock())
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertNotNull(persistentStorage.values[InstallSeenPersistentStorageKey.key])
        assertTrue(preferences.getBoolean(INSTALL_SEEN_FLAG_STORED_KEY, false))
        assertFalse(reinstallDetector.isAppReinstall())
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndExistingInstallHasNoAnswerThenAppStartStoresTheFlag() = runTest {
        val preferences = InMemorySharedPreferences()
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        reinstallDetector.onCreate(mock())
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertNotNull(persistentStorage.values[InstallSeenPersistentStorageKey.key])
        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndFlagAlreadyStoredThenAppStartDoesNotTouchBlockStore() = runTest {
        val preferences = InMemorySharedPreferences().apply {
            putBoolean(APP_REINSTALLED_KEY, false)
            putBoolean(INSTALL_SEEN_FLAG_STORED_KEY, true)
        }
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        reinstallDetector.onCreate(mock())
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertEquals(0, persistentStorage.retrieveCalls)
        assertEquals(0, persistentStorage.storeCalls)
    }

    @Test
    @Config(sdk = [34])
    fun whenAndroidVersionIs11OrAboveThenAppStartDoesNothing() = runTest {
        val preferences = InMemorySharedPreferences()
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        reinstallDetector.onCreate(mock())
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertFalse(preferences.contains(APP_REINSTALLED_KEY))
        assertFalse(ddgDownloadsDirectory.exists())
    }

    @Test
    @Config(sdk = [34])
    fun whenTwoCallersCheckAtTheSameTimeOnAFreshInstallThenBothGetTheSameAnswer() = runBlocking {
        val preferences = SlowToSaveSharedPreferences()
        val reinstallDetector = reinstallDetectorWith(preferences)

        val first = async(dispatcherProvider.io()) { reinstallDetector.isAppReinstall() }
        assertTrue(preferences.saveStarted.await(5, TimeUnit.SECONDS))
        val second = async(dispatcherProvider.io()) { reinstallDetector.isAppReinstall() }

        assertFalse(first.await())
        assertFalse(second.await())
        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
    }

    @Test
    @Config(sdk = [34])
    fun whenCallerHoldingTheLockIsCancelledThenAnswerIsStillSavedAndReused() = runBlocking {
        val preferences = SlowToSaveSharedPreferences()
        val reinstallDetector = reinstallDetectorWith(preferences)

        val first = async(dispatcherProvider.io()) { reinstallDetector.isAppReinstall() }
        assertTrue(preferences.saveStarted.await(5, TimeUnit.SECONDS))
        first.cancel()

        assertTrue(runCatching { first.await() }.exceptionOrNull() is CancellationException)
        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
        // The marker now exists, so checking Downloads again would answer "reinstall"
        assertFalse(reinstallDetector.isAppReinstall())
    }

    @Test
    @Config(sdk = [29])
    fun whenCallerHoldingTheLockIsCancelledDuringTheBlockStoreReadThenAnswerIsStillSavedAndReused() = runTest {
        val blockStoreAnswer = CompletableDeferred<Unit>()
        persistentStorage.retrieveGate = blockStoreAnswer
        val preferences = InMemorySharedPreferences()
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        val first = coroutineTestRule.testScope.async { reinstallDetector.isAppReinstall() }
        first.cancel()
        blockStoreAnswer.complete(Unit)
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertTrue(first.isCancelled)
        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
        assertFalse(reinstallDetector.isAppReinstall())
        assertEquals(1, persistentStorage.retrieveCalls)
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndTwoCallersCheckAtTheSameTimeThenBothGetTheSameAnswerFromOneBlockStoreRead() = runTest {
        val blockStoreAnswer = CompletableDeferred<Unit>()
        persistentStorage.retrieveGate = blockStoreAnswer
        val reinstallDetector = reinstallDetectorWith(
            InMemorySharedPreferences(),
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        val first = coroutineTestRule.testScope.async { reinstallDetector.isAppReinstall() }
        val second = coroutineTestRule.testScope.async { reinstallDetector.isAppReinstall() }
        blockStoreAnswer.complete(Unit)
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertFalse(first.await())
        assertFalse(second.await())
        assertEquals(1, persistentStorage.retrieveCalls)
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndSavingTheAnswerFailsThenTheFlagIsNotWritten() = runTest {
        val reinstallDetector = reinstallDetectorWith(
            FailingToSaveAnswerSharedPreferences(),
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        assertFalse(reinstallDetector.isAppReinstall())
        reinstallDetector.onCreate(mock())
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        // Without a saved answer, a flag written now would make this same install look like a reinstall on its next check
        assertEquals(0, persistentStorage.storeCalls)
        assertNull(persistentStorage.values[InstallSeenPersistentStorageKey.key])
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndBlockStoreThrowsThenIsNotReinstallAndNothingEscapes() = runTest {
        persistentStorage.throwOnCall = true
        val preferences = InMemorySharedPreferences()
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        assertFalse(reinstallDetector.isAppReinstall())
        reinstallDetector.onCreate(mock())
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
        assertTrue(persistentStorage.storeCalls > 0)
        assertTrue(coroutineTestRule.testScope.isActive)
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndFlagExistsThenCheckResultPixelSaysFound() = runTest {
        persistentStorage.values[InstallSeenPersistentStorageKey.key] = byteArrayOf(1)

        reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall()

        verifyCheckResultPixel("found")
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndFlagIsMissingThenCheckResultPixelSaysNotFound() = runTest {
        reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall()

        verifyCheckResultPixel("not_found")
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndBlockStoreIsUnavailableThenCheckResultPixelSaysUnavailable() = runTest {
        persistentStorage.available = false

        reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall()

        verifyCheckResultPixel("unavailable")
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndBlockStoreNeverAnswersThenCheckResultPixelSaysTimeout() = runTest {
        persistentStorage.neverAnswer = true
        val reinstallDetector = reinstallDetectorWith(
            InMemorySharedPreferences(),
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        coroutineTestRule.testScope.async { reinstallDetector.isAppReinstall() }
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verifyCheckResultPixel("timeout")
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndBlockStoreThrowsThenCheckResultPixelSaysError() = runTest {
        persistentStorage.throwOnCall = true
        val reinstallDetector = reinstallDetectorWith(
            InMemorySharedPreferences(),
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        reinstallDetector.isAppReinstall()

        verifyCheckResultPixel("error")
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndTwoCallersCheckAtTheSameTimeThenCheckResultPixelIsFiredOnce() = runTest {
        val blockStoreAnswer = CompletableDeferred<Unit>()
        persistentStorage.retrieveGate = blockStoreAnswer
        val reinstallDetector = reinstallDetectorWith(
            InMemorySharedPreferences(),
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        coroutineTestRule.testScope.async { reinstallDetector.isAppReinstall() }
        coroutineTestRule.testScope.async { reinstallDetector.isAppReinstall() }
        blockStoreAnswer.complete(Unit)
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel, times(1)).fire(eq(ReinstallDetectorPixelName.BLOCK_STORE_CHECK_RESULT), any(), any(), any())
    }

    @Test
    @Config(sdk = [34])
    fun whenAndroidVersionIs11OrAboveThenCheckResultPixelIsNotFired() = runTest {
        reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall()

        verify(pixel, never()).fire(eq(ReinstallDetectorPixelName.BLOCK_STORE_CHECK_RESULT), any(), any(), any())
    }

    private fun verifyCheckResultPixel(result: String) {
        verify(pixel).fire(
            eq(ReinstallDetectorPixelName.BLOCK_STORE_CHECK_RESULT),
            eq(mapOf("result" to result)),
            any(),
            eq(Unique()),
        )
    }

    private fun reinstallDetectorWith(
        preferences: SharedPreferences,
        dispatcherProvider: DispatcherProvider = this.dispatcherProvider,
        appCoroutineScope: CoroutineScope = coroutineTestRule.testScope,
    ): RealReinstallDetector {
        val sharedPreferencesProvider: SharedPreferencesProvider = mock()
        whenever(sharedPreferencesProvider.getSharedPreferences(any(), any(), any())).thenReturn(preferences)
        return RealReinstallDetector(
            dispatcherProvider = dispatcherProvider,
            sharedPreferencesProvider = sharedPreferencesProvider,
            persistentStorage = persistentStorage,
            appCoroutineScope = appCoroutineScope,
            pixel = pixel,
        )
    }

    private class FailingToSaveAnswerSharedPreferences(
        private val delegate: InMemorySharedPreferences = InMemorySharedPreferences(),
    ) : SharedPreferences by delegate, SharedPreferences.Editor by delegate {
        override fun edit(): SharedPreferences.Editor = this

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            if (key == APP_REINSTALLED_KEY) throw IllegalStateException("disk full")
            delegate.putBoolean(key, value)
            return this
        }
    }

    private class FakePersistentStorage : PersistentStorage {
        val values = mutableMapOf<String, ByteArray>()
        var available = true
        var failStore = false
        var neverAnswer = false
        var storeNeverAnswers = false
        var retrieveGate: CompletableDeferred<Unit>? = null
        var throwOnCall = false
        var retrieveCalls = 0
        var storeCalls = 0

        override suspend fun checkAvailability(): PersistentStorageAvailability =
            if (available) {
                PersistentStorageAvailability.Available(
                    isEndToEndEncryptionSupported = false,
                )
            } else {
                PersistentStorageAvailability.Unavailable
            }

        override suspend fun store(key: PersistentStorageKey, value: ByteArray): Result<Unit> {
            storeCalls++
            if (throwOnCall) throw IllegalStateException("Play services check failed")
            if (neverAnswer || storeNeverAnswers) awaitCancellation()
            if (!available) return Result.failure(PersistentStorageUnavailableException())
            if (failStore) return Result.failure(IllegalStateException("store failed"))
            values[key.key] = value
            return Result.success(Unit)
        }

        override suspend fun clear(key: PersistentStorageKey): Result<Unit> {
            values.remove(key.key)
            return Result.success(Unit)
        }

        override suspend fun retrieve(key: PersistentStorageKey): Result<ByteArray?> {
            retrieveCalls++
            if (throwOnCall) throw IllegalStateException("Play services check failed")
            if (neverAnswer) awaitCancellation()
            retrieveGate?.await()
            if (!available) return Result.failure(PersistentStorageUnavailableException())
            return Result.success(values[key.key])
        }
    }

    /**
     * Holds the first caller between creating the DuckDuckGo directory and saving its answer, which is where a second
     * caller would see the directory and wrongly answer "reinstall".
     */
    private class SlowToSaveSharedPreferences(
        private val delegate: InMemorySharedPreferences = InMemorySharedPreferences(),
    ) : SharedPreferences by delegate, SharedPreferences.Editor by delegate {
        val saveStarted = CountDownLatch(1)

        override fun edit(): SharedPreferences.Editor = this

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            saveStarted.countDown()
            Thread.sleep(SAVE_DELAY_MS)
            delegate.putBoolean(key, value)
            return this
        }
    }

    private companion object {
        const val APP_REINSTALLED_KEY = "appReinstalled"
        const val INSTALL_SEEN_FLAG_STORED_KEY = "installSeenFlagStored"
        const val SAVE_DELAY_MS = 300L
    }
}
