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

package com.duckduckgo.app.devicemigration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsDataStoreMock
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsAppUsageDao
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.UserType
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedPixelDao
import com.duckduckgo.app.browser.pageloadpixel.firstpaint.PagePaintedPixelDao
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.store.PendingPixelDao
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class MigratedInstallCleanupTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val deviceMigration: DeviceMigration = mock()
    private val pendingPixelDao: PendingPixelDao = mock()
    private val pageLoadedPixelDao: PageLoadedPixelDao = mock()
    private val pagePaintedPixelDao: PagePaintedPixelDao = mock()
    private val defaultBrowserPromptsAppUsageDao: DefaultBrowserPromptsAppUsageDao = mock()
    private val appInstallStore: AppInstallStore = mock()
    private var promptsDataStore = previousPhonePrompts(Stage.STAGE_2)
    private val preferences = InMemorySharedPreferences()
    private val sharedPreferencesProvider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doReturn preferences
    }

    init {
        runBlocking { whenever(deviceMigration.isMigrationExperienceEnabled()).thenReturn(true) }
    }

    private val testee get() = MigratedInstallCleanup(
        deviceMigration = deviceMigration,
        pendingPixelDao = pendingPixelDao,
        pageLoadedPixelDao = pageLoadedPixelDao,
        pagePaintedPixelDao = pagePaintedPixelDao,
        defaultBrowserPromptsAppUsageDao = defaultBrowserPromptsAppUsageDao,
        appInstallStore = appInstallStore,
        defaultBrowserPromptsDataStore = promptsDataStore,
        sharedPreferencesProvider = sharedPreferencesProvider,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appCoroutineScope = coroutineTestRule.testScope,
    )

    @Test
    fun whenMigratedThenPixelQueuesCleared() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        testee.onCreate(mock())

        verify(pendingPixelDao).deleteAll()
        verify(pageLoadedPixelDao).deleteAll()
        verify(pagePaintedPixelDao).deleteAll()
    }

    @Test
    fun whenMigratedThenDefaultBrowserDialogCountReset() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        testee.onCreate(mock())

        verify(appInstallStore).newDefaultBrowserDialogCount = 0
    }

    @Test
    fun whenMigratedThenDefaultBrowserPromptUsageCleared() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        testee.onCreate(mock())

        verify(defaultBrowserPromptsAppUsageDao).deleteAll()
    }

    @Test
    fun whenNotMigratedThenNothingCleared() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(false)

        testee.onCreate(mock())

        verifyNothingCleared()
        assertFalse(preferences.getBoolean("cleanupDone", false))
    }

    @Test
    fun whenAlreadyCleanedThenNothingClearedAgain() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        testee.onCreate(mock())
        clearInvocations(
            pendingPixelDao,
            pageLoadedPixelDao,
            pagePaintedPixelDao,
            defaultBrowserPromptsAppUsageDao,
            appInstallStore,
        )

        testee.onCreate(mock())

        verifyNothingCleared()
    }

    @Test
    fun whenDeleteFailsThenGuardNotSet() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(pageLoadedPixelDao.deleteAll()).thenThrow(IllegalStateException("db closed"))

        testee.onCreate(mock())

        assertFalse(preferences.getBoolean("cleanupDone", false))
    }

    @Test
    fun whenCleanedThenGuardSet() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        testee.onCreate(mock())

        assertTrue(preferences.getBoolean("cleanupDone", false))
    }

    private fun verifyNothingCleared() {
        verify(pendingPixelDao, never()).deleteAll()
        verify(pageLoadedPixelDao, never()).deleteAll()
        verify(pagePaintedPixelDao, never()).deleteAll()
        verify(defaultBrowserPromptsAppUsageDao, never()).deleteAll()
        verify(appInstallStore, never()).newDefaultBrowserDialogCount = 0
    }

    @Test
    fun whenFeatureDisabledThenNothingClearedAndGuardNotSet() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(deviceMigration.isMigrationExperienceEnabled()).thenReturn(false)

        testee.onCreate(mock())

        verifyNothingCleared()
        assertFalse(preferences.getBoolean("cleanupDone", false))
    }

    @Test
    fun whenFeatureEnabledAfterBeingDisabledThenClearedOnce() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(deviceMigration.isMigrationExperienceEnabled()).thenReturn(false)
        testee.onCreate(mock())

        whenever(deviceMigration.isMigrationExperienceEnabled()).thenReturn(true)
        testee.onCreate(mock())
        testee.onCreate(mock())

        verify(pendingPixelDao, times(1)).deleteAll()
        assertTrue(preferences.getBoolean("cleanupDone", false))
    }

    @Test
    fun whenMigratedThenDefaultBrowserPromptsFromPreviousPhoneRestart() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        testee.onCreate(mock())

        assertEquals(Stage.NOT_STARTED, promptsDataStore.stage.first())
        assertFalse(promptsDataStore.showSetAsDefaultPopupMenuItem.first())
        assertFalse(promptsDataStore.highlightPopupMenu.first())
        assertFalse(promptsDataStore.showSetAsDefaultMessage.first())
    }

    @Test
    fun whenMigratedAndPromptsStoppedOnPreviousPhoneThenStageKept() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        promptsDataStore = previousPhonePrompts(Stage.STOPPED)

        testee.onCreate(mock())

        assertEquals(Stage.STOPPED, promptsDataStore.stage.first())
    }

    @Test
    fun whenNotMigratedThenDefaultBrowserPromptsKept() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(false)

        testee.onCreate(mock())

        assertEquals(Stage.STAGE_2, promptsDataStore.stage.first())
        assertTrue(promptsDataStore.showSetAsDefaultMessage.first())
    }

    @Test
    fun whenMigratedAndNotCleanedYetThenPending() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        assertTrue(testee.isPending())
    }

    @Test
    fun whenMigratedAndCleanedThenNotPending() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        val cleanup = testee
        cleanup.onCreate(mock())

        assertFalse(cleanup.isPending())
    }

    @Test
    fun whenNotMigratedThenNotPending() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(false)

        assertFalse(testee.isPending())
    }

    @Test
    fun whenFeatureDisabledThenNotPending() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(deviceMigration.isMigrationExperienceEnabled()).thenReturn(false)

        assertFalse(testee.isPending())
    }

    private fun previousPhonePrompts(stage: Stage) = DefaultBrowserPromptsDataStoreMock(
        initialStage = stage,
        userType = UserType.EXISTING,
        initialShowPopupMenuItem = true,
        initialHighlightPopupMenuIcon = true,
        initialShowSetAsDefaultMessage = true,
    )
}
