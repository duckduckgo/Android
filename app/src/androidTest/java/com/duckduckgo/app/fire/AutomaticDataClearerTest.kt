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
import com.duckduckgo.app.dispatchers.IntentDispatcherActivity
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
        testee = createTestee(coroutineTestRule.testDispatcherProvider)
    }

    private fun createTestee(dispatchers: DispatcherProvider) = AutomaticDataClearer(
        workManager = workManager,
        settingsDataStore = settingsDataStore,
        clearDataAction = clearDataAction,
        dataClearing = dataClearing,
        dataClearerForegroundAppRestartPixel = restartPixel,
        dispatchers = dispatchers,
        fireDataStore = fireDataStore,
        dataClearingWideEvent = dataClearingWideEvent,
        customTabsFeature = customTabsFeature,
    )

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
    fun whenOpenedByCustomTabThenAppIsMarkedAsUsed() = runTest {
        testee.onActivityStarted(CustomTabActivity())
        testee.onOpen(isFreshLaunch = true)

        verify(settingsDataStore).appUsedSinceLastClear = true
    }

    @UiThreadTest
    @Test
    fun whenBrowserStartsAfterCustomTabFreshLaunchThenDataIsClearedAsFreshLaunch() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(true, true, false)).thenReturn(true)
        whenever(dataClearing.clearDataUsingAutomaticFireOptions(false)).thenReturn(false)
        whenever(fireDataStore.getAutomaticallyClearWhenOption()).thenReturn(ClearWhenOption.APP_EXIT_ONLY)
        whenever(fireDataStore.getAutomaticClearOptions()).thenReturn(emptySet())

        val customTab = CustomTabActivity()
        testee.onActivityStarted(customTab)
        testee.onOpen(isFreshLaunch = true)
        testee.onActivityStopped(customTab)
        testee.onClose()
        testee.onOpen(isFreshLaunch = false)
        testee.onActivityStarted(Activity())

        verify(dataClearing).clearDataUsingAutomaticFireOptions(false)
    }

    @UiThreadTest
    @Test
    fun whenBrowserStartsUnderDispatcherThenDataIsNotCleared() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(any(), any(), any())).thenReturn(true)

        val dispatcher = IntentDispatcherActivity()
        testee.onActivityCreated(dispatcher, null)
        testee.onActivityStarted(dispatcher)
        testee.onOpen(isFreshLaunch = false)
        testee.onActivityStarted(Activity())
        testee.onActivityStarted(CustomTabActivity())
        testee.onActivityStopped(dispatcher)
        testee.onActivityDestroyed(dispatcher)

        verify(dataClearing, never()).clearDataUsingAutomaticFireOptions(any())
    }

    @UiThreadTest
    @Test
    fun whenDispatcherOpensBrowserThenDataIsClearedOnceDispatcherIsDestroyed() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(any(), any(), any())).thenReturn(true)
        whenever(dataClearing.clearDataUsingAutomaticFireOptions(false)).thenReturn(false)

        val dispatcher = IntentDispatcherActivity()
        testee.onActivityCreated(dispatcher, null)
        testee.onActivityStarted(dispatcher)
        testee.onOpen(isFreshLaunch = false)
        testee.onActivityStarted(Activity())
        testee.onActivityStopped(dispatcher)
        verify(dataClearing, never()).clearDataUsingAutomaticFireOptions(any())

        testee.onActivityDestroyed(dispatcher)

        verify(dataClearing).clearDataUsingAutomaticFireOptions(false)
    }

    @UiThreadTest
    @Test
    fun whenCustomTabStopsWithBrowserInFrontThenDataIsCleared() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(any(), any(), any())).thenReturn(true)
        whenever(dataClearing.clearDataUsingAutomaticFireOptions(false)).thenReturn(false)

        val customTab = CustomTabActivity()
        testee.onActivityStarted(customTab)
        testee.onOpen(isFreshLaunch = false)
        testee.onActivityStarted(Activity())
        verify(dataClearing, never()).clearDataUsingAutomaticFireOptions(any())

        testee.onActivityStopped(customTab)

        verify(dataClearing).clearDataUsingAutomaticFireOptions(false)
    }

    @UiThreadTest
    @Test
    fun whenOpenIsReportedBeforeCustomTabStartsThenDataIsNotCleared() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(any(), any(), any())).thenReturn(true)
        val queuedDispatcher = StandardTestDispatcher(testScheduler)
        val testee = createTestee(
            object : DispatcherProvider {
                override fun computation(): CoroutineDispatcher = queuedDispatcher
                override fun io(): CoroutineDispatcher = queuedDispatcher
                override fun main(): CoroutineDispatcher = queuedDispatcher
                override fun unconfined(): CoroutineDispatcher = queuedDispatcher
            },
        )

        testee.onOpen(isFreshLaunch = false)
        testee.onActivityStarted(CustomTabActivity())
        advanceUntilIdle()

        verify(dataClearing, never()).clearDataUsingAutomaticFireOptions(any())
        verify(workManager).cancelAllWorkByTag(DataClearingWorker.WORK_REQUEST_TAG)
    }

    @UiThreadTest
    @Test
    fun whenBrowserStartsBeforeDispatcherThenDataIsNotCleared() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(any(), any(), any())).thenReturn(true)

        val dispatcher = IntentDispatcherActivity()
        testee.onActivityCreated(dispatcher, null)
        testee.onActivityStarted(Activity())
        testee.onOpen(isFreshLaunch = false)
        testee.onActivityStarted(dispatcher)
        testee.onActivityStarted(CustomTabActivity())
        testee.onActivityStopped(dispatcher)
        testee.onActivityDestroyed(dispatcher)

        verify(dataClearing, never()).clearDataUsingAutomaticFireOptions(any())
    }

    @Test
    fun whenBrowserOpensFromCustomTabWhileClearIsPendingThenRestartKeepsTheQuery() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(false, true, false)).thenReturn(true)
        whenever(dataClearing.clearDataUsingAutomaticFireOptions(false)).thenReturn(true)
        lateinit var customTab: CustomTabActivity
        lateinit var dispatcher: IntentDispatcherActivity
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            customTab = CustomTabActivity()
            dispatcher = IntentDispatcherActivity()
        }

        testee.isFreshAppLaunch = false
        testee.onActivityStarted(customTab)
        testee.onOpen(isFreshLaunch = false)
        testee.onActivityCreated(dispatcher, null)
        testee.onActivityStarted(dispatcher)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { testee.onActivityStarted(Activity()) }
        testee.deferredQuery = "https://example.com"
        testee.onActivityStopped(customTab)
        testee.onActivityStopped(dispatcher)
        testee.onActivityDestroyed(dispatcher)
        Thread.sleep(200)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        verify(clearDataAction).killAndRestartProcess(notifyDataCleared = true, query = "https://example.com")
    }

    @UiThreadTest
    @Test
    fun whenOnlyCustomTabStopsThenDataIsNotCleared() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(any(), any(), any())).thenReturn(true)

        val customTab = CustomTabActivity()
        testee.onActivityStarted(customTab)
        testee.onOpen(isFreshLaunch = false)
        testee.onActivityStopped(customTab)

        verify(dataClearing, never()).clearDataUsingAutomaticFireOptions(any())
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
        verify(clearDataAction, never()).killAndRestartProcess(any(), any(), any(), anyOrNull())
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
    fun whenAutomaticClearNeedsRestartWithDeferredQueryThenQueryIsPassedToRestart() = runTest {
        whenever(settingsDataStore.appUsedSinceLastClear).thenReturn(true)
        whenever(dataClearing.shouldClearDataAutomatically(false, true, false)).thenReturn(true)
        whenever(dataClearing.clearDataUsingAutomaticFireOptions(false)).thenReturn(true)

        testee.isFreshAppLaunch = false
        testee.deferredQuery = "https://example.com"
        testee.onAppForegroundedAsync()
        Thread.sleep(200)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        verify(clearDataAction).killAndRestartProcess(notifyDataCleared = true, query = "https://example.com")
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
