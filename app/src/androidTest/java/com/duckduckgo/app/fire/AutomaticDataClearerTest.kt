/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.fire

import android.app.Activity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.duckduckgo.app.browser.customtabs.CustomTabActivity
import com.duckduckgo.app.browser.customtabs.CustomTabsFeature
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AutomaticDataClearerTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

    private val settingsDataStore: SettingsDataStore = mock()
    private val clearDataAction: ClearDataAction = mock()
    private val workManager: WorkManager = mock()
    private val dataClearing: AutomaticDataClearing = mock()
    private val fireDataStore: FireDataStore = mock()
    private val dataClearingWideEvent: DataClearingWideEvent = mock()
    private val restartPixel = DataClearerForegroundAppRestartPixel(
        InstrumentationRegistry.getInstrumentation().targetContext,
        mock<Pixel>(),
        coroutineTestRule.testScope,
        coroutineTestRule.testDispatcherProvider,
    )

    private val customTabsFeature = FakeFeatureToggleFactory.create(CustomTabsFeature::class.java).apply {
        keepCustomTabOnAppSwitch().setRawStoredState(State(enable = true))
    }

    private lateinit var testee: AutomaticDataClearer

    @UiThreadTest
    @Before
    fun setup() {
        testee = AutomaticDataClearer(
            workManager = workManager,
            settingsDataStore = settingsDataStore,
            clearDataAction = clearDataAction,
            dataClearing = dataClearing,
            dataClearerForegroundAppRestartPixel = restartPixel,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            fireDataStore = fireDataStore,
            dataClearingWideEvent = dataClearingWideEvent,
            customTabsFeature = customTabsFeature,
        )
    }

    @UiThreadTest
    @Test
    fun whenOpenedByCustomTabThenDataIsNotCleared() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(any(), any(), any())).thenReturn(true)

        testee.onActivityStarted(CustomTabActivity())
        testee.onOpen(isFreshLaunch = true)

        verify(dataClearing, never()).clearDataUsingAutomaticFireOptions(any())
        verify(workManager).cancelAllWorkByTag(DataClearingWorker.WORK_REQUEST_TAG)
    }

    @UiThreadTest
    @Test
    fun whenBrowserStartsAfterCustomTabFreshLaunchThenDataIsClearedAsFreshLaunch() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(true, true, false)).thenReturn(true)
        whenever(dataClearing.clearDataUsingAutomaticFireOptions(false)).thenReturn(false)
        whenever(fireDataStore.getAutomaticallyClearWhenOption()).thenReturn(ClearWhenOption.APP_EXIT_ONLY)
        whenever(fireDataStore.getAutomaticClearOptions()).thenReturn(emptySet())

        testee.onActivityStarted(CustomTabActivity())
        testee.onOpen(isFreshLaunch = true)
        testee.onClose()
        testee.onOpen(isFreshLaunch = false)
        testee.onActivityStarted(Activity())

        verify(dataClearing).clearDataUsingAutomaticFireOptions(false)
    }

    @UiThreadTest
    @Test
    fun whenClosedWhileClearIsPendingThenBackgroundTimestampIsKept() = runTest {
        whenever(fireDataStore.getAutomaticallyClearWhenOption()).thenReturn(ClearWhenOption.APP_EXIT_ONLY)
        whenever(fireDataStore.getAutomaticClearOptions()).thenReturn(emptySet())

        testee.onActivityStarted(CustomTabActivity())
        testee.onOpen(isFreshLaunch = false)
        testee.onClose()

        verify(settingsDataStore, never()).appBackgroundedTimestamp = any()
    }

    @UiThreadTest
    @Test
    fun whenOpenedByCustomTabAndKeepCustomTabDisabledThenDataIsCleared() = runTest {
        customTabsFeature.keepCustomTabOnAppSwitch().setRawStoredState(State(enable = false))
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(true, true, false)).thenReturn(true)
        whenever(dataClearing.clearDataUsingAutomaticFireOptions(false)).thenReturn(false)

        testee.onActivityStarted(CustomTabActivity())
        testee.onOpen(isFreshLaunch = true)

        verify(dataClearing).clearDataUsingAutomaticFireOptions(false)
    }

    @UiThreadTest
    @Test
    fun whenAutomaticClearNotNeededThenDataIsNotCleared() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(true, true, false)).thenReturn(false)

        testee.isFreshAppLaunch = true
        testee.onAppForegroundedAsync()

        verify(dataClearing, never()).clearDataUsingAutomaticFireOptions(any())
    }

    @UiThreadTest
    @Test
    fun whenAutomaticClearNeededOnFreshLaunchThenDataIsClearedWithoutRestart() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(true, true, false)).thenReturn(true)
        whenever(dataClearing.clearDataUsingAutomaticFireOptions(false)).thenReturn(true)

        testee.isFreshAppLaunch = true
        testee.onAppForegroundedAsync()

        verify(dataClearing).clearDataUsingAutomaticFireOptions(false)
        verify(clearDataAction, never()).killAndRestartProcess(any(), any(), any())
    }

    @Test
    fun whenAutomaticClearNeedsRestartAfterResumeThenProcessIsRestarted() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(false, true, false)).thenReturn(true)
        whenever(dataClearing.clearDataUsingAutomaticFireOptions(false)).thenReturn(true)

        testee.isFreshAppLaunch = false
        testee.onAppForegroundedAsync()
        Thread.sleep(200)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        verify(clearDataAction).setAppUsedSinceLastClearFlag(false)
        verify(clearDataAction).killAndRestartProcess(notifyDataCleared = true)
    }

    @Test
    fun whenAutomaticOptionsSelectedOnExitThenProcessIsKilled() = runTest {
        whenever(dataClearing.isAutomaticDataClearingOptionSelected()).thenReturn(true)

        testee.onExit()

        verify(clearDataAction).killProcess()
    }

    @Test
    fun whenNoAutomaticOptionsSelectedOnExitThenProcessIsNotKilled() = runTest {
        whenever(dataClearing.isAutomaticDataClearingOptionSelected()).thenReturn(false)

        testee.onExit()

        verify(clearDataAction, never()).killProcess()
    }

    @Test
    fun whenNoAutomaticOptionsSelectedOnCloseThenTimerIsNotScheduled() = runTest {
        whenever(fireDataStore.getAutomaticClearOptions()).thenReturn(emptySet())
        whenever(fireDataStore.getAutomaticallyClearWhenOption()).thenReturn(ClearWhenOption.APP_EXIT_OR_15_MINS)

        testee.onClose()
        coroutineTestRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(workManager, never()).enqueue(argThat<List<WorkRequest>> { size == 1 && first() is OneTimeWorkRequest })
    }

    @Test
    fun whenAutomaticOptionsUseExitOnlyThenTimerIsNotScheduled() = runTest {
        whenever(fireDataStore.getAutomaticClearOptions()).thenReturn(setOf(FireClearOption.DATA))
        whenever(fireDataStore.getAutomaticallyClearWhenOption()).thenReturn(ClearWhenOption.APP_EXIT_ONLY)

        testee.onClose()
        coroutineTestRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(workManager, never()).enqueue(argThat<List<WorkRequest>> { size == 1 && first() is OneTimeWorkRequest })
    }

    @Test
    fun whenAutomaticOptionsUseDelayThenTimerIsScheduled() = runTest {
        whenever(fireDataStore.getAutomaticClearOptions()).thenReturn(setOf(FireClearOption.DATA))
        whenever(fireDataStore.getAutomaticallyClearWhenOption()).thenReturn(ClearWhenOption.APP_EXIT_OR_15_MINS)

        testee.onClose()
        coroutineTestRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(workManager).enqueue(argThat<List<WorkRequest>> { size == 1 && first() is OneTimeWorkRequest })
    }
}
