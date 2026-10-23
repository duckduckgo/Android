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

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.common.utils.CurrentTimeProvider
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class RealReinstallDetectorTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val dispatcherProvider = DefaultDispatcherProvider()
    private val persistentStorage = FakePersistentStorage()
    private val pixel: Pixel = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val packageManager: PackageManager = mock()
    private val context: Context = mock()
    private val outOfSightLaunchDetector = FakeOutOfSightLaunchDetector()
    private val ddgDownloadsDirectory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "DuckDuckGo",
    )

    @Before
    fun setUp() {
        ddgDownloadsDirectory.deleteRecursively()
        whenever(appBuildConfig.isNewInstall()).thenReturn(true)
        whenever(context.packageName).thenReturn(PACKAGE_NAME)
        whenever(context.packageManager).thenReturn(packageManager)
        // Unless a test says otherwise, the app was installed long before any folder a test creates
        givenFirstInstallTime(System.currentTimeMillis() - ONE_DAY_MS)
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
    fun whenSavingTheAnswerFailsThenIsNotReinstallAndDirectoryIsNotCreated() = runTest {
        assertFalse(reinstallDetectorWith(FailingToSaveSharedPreferences()).isAppReinstall())
        // With the folder in place but no saved answer, the next launch would answer "reinstall" for this new install
        assertFalse(ddgDownloadsDirectory.exists())
    }

    @Test
    @Config(sdk = [34])
    fun whenDdgDownloadsDirectoryWasCreatedSecondsBeforeTheInstallThenIsNotReinstall() = runTest {
        val installedAt = System.currentTimeMillis()
        givenFirstInstallTime(installedAt)
        givenDdgDownloadsDirectoryLastChangedAt(installedAt - 3_000L)
        val preferences = InMemorySharedPreferences()

        assertFalse(reinstallDetectorWith(preferences).isAppReinstall())
        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
    }

    @Test
    @Config(sdk = [34])
    fun whenDdgDownloadsDirectoryWasCreatedSecondsAfterTheInstallThenIsNotReinstall() = runTest {
        val installedAt = System.currentTimeMillis() - 10_000L
        givenFirstInstallTime(installedAt)
        givenDdgDownloadsDirectoryLastChangedAt(installedAt + 5_000L)

        assertFalse(reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall())
    }

    @Test
    @Config(sdk = [34])
    fun whenDdgDownloadsDirectoryIsOlderThanTheSameInstallWindowThenIsReinstall() = runTest {
        val installedAt = System.currentTimeMillis()
        givenFirstInstallTime(installedAt)
        givenDdgDownloadsDirectoryLastChangedAt(installedAt - 3 * 60 * 1000L)

        assertTrue(reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall())
    }

    @Test
    @Config(sdk = [34])
    fun whenInstallTimeIsUnavailableThenExistingDdgDownloadsDirectoryStillMeansReinstall() = runTest {
        whenever(packageManager.getPackageInfo(PACKAGE_NAME, 0)).thenThrow(PackageManager.NameNotFoundException())
        givenDdgDownloadsDirectoryLastChangedAt(System.currentTimeMillis())

        assertTrue(reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall())
    }

    @Test
    @Config(sdk = [34])
    fun whenDdgDownloadsDirectoryWasCreatedSecondsBeforeAGalaxyStoreInstallThenRecentFolderPixelIsFired() = runTest {
        val installedAt = System.currentTimeMillis()
        givenFirstInstallTime(installedAt)
        givenInstaller(GALAXY_STORE_PACKAGE)
        givenDdgDownloadsDirectoryLastChangedAt(installedAt - 3_000L)

        reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall()

        verifyRecentFolderPixel(age = "0-10s", installer = "galaxy_store")
    }

    @Test
    @Config(sdk = [34])
    fun whenDdgDownloadsDirectoryWasChangedAMinuteAfterAPlayStoreInstallThenRecentFolderPixelSaysUpTo2Minutes() = runTest {
        val installedAt = System.currentTimeMillis() - 2 * 60 * 1000L
        givenFirstInstallTime(installedAt)
        givenInstaller(PLAY_STORE_PACKAGE)
        givenDdgDownloadsDirectoryLastChangedAt(installedAt + 60_000L)

        reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall()

        verifyRecentFolderPixel(age = "10s-2m", installer = "play_store")
    }

    @Test
    @Config(sdk = [34])
    fun whenDdgDownloadsDirectoryIsOutsideTheSameInstallWindowButRecentThenRecentFolderPixelIsFiredForAReinstall() = runTest {
        val installedAt = System.currentTimeMillis()
        givenFirstInstallTime(installedAt)
        givenDdgDownloadsDirectoryLastChangedAt(installedAt - 5 * 60 * 1000L)

        assertTrue(reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall())

        verifyRecentFolderPixel(age = "2-10m", installer = "other")
    }

    @Test
    @Config(sdk = [34])
    fun whenInstallerCannotBeReadThenRecentFolderPixelSaysOther() = runTest {
        val installedAt = System.currentTimeMillis()
        givenFirstInstallTime(installedAt)
        whenever(packageManager.getInstallSourceInfo(PACKAGE_NAME)).thenThrow(PackageManager.NameNotFoundException())
        givenDdgDownloadsDirectoryLastChangedAt(installedAt - 3_000L)

        reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall()

        verifyRecentFolderPixel(age = "0-10s", installer = "other")
    }

    @Test
    @Config(sdk = [34])
    fun whenDdgDownloadsDirectoryIsOlderThan10MinutesThenRecentFolderPixelIsNotFired() = runTest {
        givenDdgDownloadsDirectoryLastChangedAt(System.currentTimeMillis())

        assertTrue(reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall())

        verify(pixel, never()).fire(eq(ReinstallDetectorPixelName.RECENT_FOLDER), any(), any(), any())
    }

    @Test
    @Config(sdk = [34])
    fun whenDdgDownloadsDirectoryIsMissingThenRecentFolderPixelIsNotFired() = runTest {
        reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall()

        verify(pixel, never()).fire(eq(ReinstallDetectorPixelName.RECENT_FOLDER), any(), any(), any())
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
    fun whenLaunchedOutOfSightThenIsNotReinstallAndNothingIsCreatedOrSaved() = runTest {
        outOfSightLaunchDetector.isOutOfSightLaunch = true
        val preferences = InMemorySharedPreferences()

        assertFalse(reinstallDetectorWith(preferences).isAppReinstall())

        assertFalse(ddgDownloadsDirectory.exists())
        assertFalse(preferences.contains(APP_REINSTALLED_KEY))
    }

    @Test
    @Config(sdk = [34])
    fun whenLaunchedOutOfSightAndAFolderAlreadyExistsThenItIsNotReadAndNothingIsSaved() = runTest {
        outOfSightLaunchDetector.isOutOfSightLaunch = true
        givenDdgDownloadsDirectoryLastChangedAt(System.currentTimeMillis() - ONE_DAY_MS)
        val preferences = InMemorySharedPreferences()

        assertFalse(reinstallDetectorWith(preferences).isAppReinstall())

        assertFalse(preferences.contains(APP_REINSTALLED_KEY))
        verify(pixel, never()).fire(eq(ReinstallDetectorPixelName.RECENT_FOLDER), any(), any(), any())
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndLaunchedOutOfSightThenAppStartDoesNotTouchBlockStore() = runTest {
        outOfSightLaunchDetector.isOutOfSightLaunch = true
        val preferences = InMemorySharedPreferences()
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        reinstallDetector.onCreate(mock())
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertEquals(0, persistentStorage.retrieveCalls)
        assertEquals(0, persistentStorage.storeCalls)
        assertFalse(preferences.contains(APP_REINSTALLED_KEY))
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndOutOfSightLaunchIsSeenDuringTheCheckThenTheFlagIsNotWritten() = runTest {
        val preferences = InMemorySharedPreferences()
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )
        persistentStorage.retrieveGate = CompletableDeferred()

        reinstallDetector.onCreate(mock())
        coroutineTestRule.testScope.testScheduler.runCurrent()
        // The launch activity sees the display while app start is still reading Block Store
        outOfSightLaunchDetector.isOutOfSightLaunch = true
        persistentStorage.retrieveGate?.complete(Unit)
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertEquals(1, persistentStorage.retrieveCalls)
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
    fun whenTwoCallersCheckAtTheSameTimeOnAFreshInstallThenTheCheckRunsOnceAndBothGetTheSameAnswer() = runBlocking {
        val preferences = SlowToSaveSharedPreferences()
        val reinstallDetector = reinstallDetectorWith(preferences)

        val first = async(dispatcherProvider.io()) { reinstallDetector.isAppReinstall() }
        assertTrue(preferences.saveStarted.await(5, TimeUnit.SECONDS))
        val second = async(dispatcherProvider.io()) { reinstallDetector.isAppReinstall() }

        assertFalse(first.await())
        assertFalse(second.await())
        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
        assertEquals(1, preferences.saves.get())
    }

    @Test
    @Config(sdk = [34])
    fun whenFirstCallerIsCancelledThenAnswerIsStillSavedAndReused() = runBlocking {
        val preferences = SlowToSaveSharedPreferences()
        val reinstallDetector = reinstallDetectorWith(preferences)

        val first = async(dispatcherProvider.io()) { reinstallDetector.isAppReinstall() }
        assertTrue(preferences.saveStarted.await(5, TimeUnit.SECONDS))
        first.cancel()

        assertTrue(runCatching { first.await() }.exceptionOrNull() is CancellationException)
        // The marker now exists, so checking Downloads again would answer "reinstall"
        assertFalse(reinstallDetector.isAppReinstall())
        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
        assertTrue(ddgDownloadsDirectory.exists())
    }

    @Test
    @Config(sdk = [29])
    fun whenFirstCallerIsCancelledDuringTheBlockStoreReadThenAnswerIsStillSavedAndReused() = runTest {
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
    fun whenBelowAndroid11AndSavingTheAnswerReturnsFalseThenTheFlagIsNotWrittenEvenOnAppStart() = runTest {
        val preferences = NotReachingDiskSharedPreferences()
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        assertFalse(reinstallDetector.isAppReinstall())
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        reinstallDetector.onCreate(mock())
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        // The answer is still readable in memory, but after a restart only the flag would be left
        assertTrue(preferences.contains(APP_REINSTALLED_KEY))
        assertEquals(0, persistentStorage.storeCalls)
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndTheAnswerIsStillBeingSavedThenAppStartWritesTheFlagOnlyAfterTheSave() = runBlocking {
        val preferences = SlowToCommitAnswerSharedPreferences()
        val appCoroutineScope = CoroutineScope(SupervisorJob() + dispatcherProvider.io())
        val reinstallDetector = reinstallDetectorWith(preferences, appCoroutineScope = appCoroutineScope)

        try {
            val first = async(dispatcherProvider.io()) { reinstallDetector.isAppReinstall() }
            assertTrue(preferences.commitStarted.await(5, TimeUnit.SECONDS))
            reinstallDetector.onCreate(mock())

            assertFalse(persistentStorage.stored.await(SAVE_DELAY_MS, TimeUnit.MILLISECONDS))
            preferences.releaseCommit.countDown()

            assertFalse(first.await())
            assertTrue(persistentStorage.stored.await(5, TimeUnit.SECONDS))
            assertNotNull(persistentStorage.values[InstallSeenPersistentStorageKey.key])
        } finally {
            preferences.releaseCommit.countDown()
            appCoroutineScope.cancel()
        }
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
    @Config(sdk = [29])
    fun whenBelowAndroid11AndAnUpdatedInstallRunsTheCheckThenTheFlagIsStoredButCheckResultPixelIsNotFired() = runTest {
        whenever(appBuildConfig.isNewInstall()).thenReturn(false)
        val preferences = InMemorySharedPreferences()
        val reinstallDetector = reinstallDetectorWith(
            preferences,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        reinstallDetector.onCreate(mock())
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
        assertNotNull(persistentStorage.values[InstallSeenPersistentStorageKey.key])
        verify(pixel, never()).fire(eq(ReinstallDetectorPixelName.BLOCK_STORE_CHECK_RESULT), any(), any(), any())
    }

    @Test
    @Config(sdk = [34])
    fun whenAndroidVersionIs11OrAboveThenCheckResultPixelIsNotFired() = runTest {
        reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall()

        verify(pixel, never()).fire(eq(ReinstallDetectorPixelName.BLOCK_STORE_CHECK_RESULT), any(), any(), any())
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11ThenCheckResultPixelReportsHowLongTheBlockStoreReadTook() = runTest {
        // Keeps every check a fresh install: a stored flag would make the next one find it
        persistentStorage.failStore = true
        val expectedBuckets = mapOf(
            500L to "0-0.5",
            1_500L to "0.5-1.5",
            3_000L to "1.5-3",
            5_000L to "3-5",
            9_999L to "5-10",
            10_000L to "10+",
        )

        expectedBuckets.forEach { (durationMillis, bucket) ->
            whenever(currentTimeProvider.elapsedRealtime()).thenReturn(0L, durationMillis)

            reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall()

            verifyCheckResultPixel("not_found", duration = bucket)
        }
    }

    private fun verifyCheckResultPixel(result: String, duration: String = "0-0.5") {
        verify(pixel).fire(
            eq(ReinstallDetectorPixelName.BLOCK_STORE_CHECK_RESULT),
            eq(mapOf("result" to result, "duration" to duration)),
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
            context = context,
            dispatcherProvider = dispatcherProvider,
            sharedPreferencesProvider = sharedPreferencesProvider,
            persistentStorage = persistentStorage,
            appCoroutineScope = appCoroutineScope,
            pixel = pixel,
            currentTimeProvider = currentTimeProvider,
            appBuildConfig = appBuildConfig,
            outOfSightLaunchDetector = outOfSightLaunchDetector,
        )
    }

    private fun givenFirstInstallTime(installedAt: Long) {
        whenever(packageManager.getPackageInfo(PACKAGE_NAME, 0)).thenReturn(PackageInfo().apply { firstInstallTime = installedAt })
    }

    private fun givenDdgDownloadsDirectoryLastChangedAt(changedAt: Long) {
        ddgDownloadsDirectory.mkdirs()
        assertTrue(ddgDownloadsDirectory.setLastModified(changedAt))
    }

    private fun givenInstaller(installerPackage: String) {
        val installSourceInfo: InstallSourceInfo = mock()
        whenever(installSourceInfo.installingPackageName).thenReturn(installerPackage)
        whenever(packageManager.getInstallSourceInfo(PACKAGE_NAME)).thenReturn(installSourceInfo)
    }

    private fun verifyRecentFolderPixel(age: String, installer: String) {
        verify(pixel).fire(
            eq(ReinstallDetectorPixelName.RECENT_FOLDER),
            eq(mapOf("age" to age, "installer" to installer)),
            any(),
            eq(Unique()),
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

    /**
     * Like Android when the disk write fails: commit() returns false, but the value stays readable in memory.
     */
    private class NotReachingDiskSharedPreferences(
        private val delegate: InMemorySharedPreferences = InMemorySharedPreferences(),
    ) : SharedPreferences by delegate, SharedPreferences.Editor by delegate {
        override fun edit(): SharedPreferences.Editor = this

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            delegate.putBoolean(key, value)
            return this
        }

        override fun commit(): Boolean = false
    }

    /**
     * Holds the answer's commit open after the answer is readable in memory, like Android while the disk write is running.
     */
    private class SlowToCommitAnswerSharedPreferences(
        private val delegate: InMemorySharedPreferences = InMemorySharedPreferences(),
    ) : SharedPreferences by delegate, SharedPreferences.Editor by delegate {
        val commitStarted = CountDownLatch(1)
        val releaseCommit = CountDownLatch(1)

        @Volatile
        private var lastPutKey: String? = null

        override fun edit(): SharedPreferences.Editor = this

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            lastPutKey = key
            delegate.putBoolean(key, value)
            return this
        }

        override fun commit(): Boolean {
            if (lastPutKey == APP_REINSTALLED_KEY) {
                commitStarted.countDown()
                releaseCommit.await(5, TimeUnit.SECONDS)
            }
            return true
        }
    }

    private class FakeOutOfSightLaunchDetector : OutOfSightLaunchDetector {
        @Volatile
        override var isOutOfSightLaunch: Boolean = false

        override fun onLaunchDisplay(display: Display?): Boolean = false
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
        val stored = CountDownLatch(1)

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
            stored.countDown()
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
     * Holds the check while it saves its answer. A second check started meanwhile would find neither the answer nor the
     * DuckDuckGo directory and save again.
     */
    private class SlowToSaveSharedPreferences(
        private val delegate: InMemorySharedPreferences = InMemorySharedPreferences(),
    ) : SharedPreferences by delegate, SharedPreferences.Editor by delegate {
        val saveStarted = CountDownLatch(1)
        val saves = AtomicInteger(0)

        override fun edit(): SharedPreferences.Editor = this

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            saves.incrementAndGet()
            saveStarted.countDown()
            Thread.sleep(SAVE_DELAY_MS)
            delegate.putBoolean(key, value)
            return this
        }
    }

    private class FailingToSaveSharedPreferences(
        private val delegate: InMemorySharedPreferences = InMemorySharedPreferences(),
    ) : SharedPreferences by delegate, SharedPreferences.Editor by delegate {
        override fun edit(): SharedPreferences.Editor = this

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            delegate.putBoolean(key, value)
            return this
        }

        override fun commit(): Boolean = false
    }

    private companion object {
        const val APP_REINSTALLED_KEY = "appReinstalled"
        const val INSTALL_SEEN_FLAG_STORED_KEY = "installSeenFlagStored"
        const val SAVE_DELAY_MS = 300L
        const val PACKAGE_NAME = "com.duckduckgo.mobile.android"
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
        const val GALAXY_STORE_PACKAGE = "com.sec.android.app.samsungapps"
        const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
