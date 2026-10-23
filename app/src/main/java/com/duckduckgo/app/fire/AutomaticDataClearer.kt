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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.core.os.postDelayed
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.app.browser.customtabs.CustomTabActivity
import com.duckduckgo.app.browser.customtabs.CustomTabsFeature
import com.duckduckgo.app.dispatchers.IntentDispatcherActivity
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.global.ApplicationClearDataState
import com.duckduckgo.app.global.ApplicationClearDataState.FINISHED
import com.duckduckgo.app.global.ApplicationClearDataState.INITIALIZING
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

interface DataClearer {
    val dataClearerState: LiveData<ApplicationClearDataState>
    var isFreshAppLaunch: Boolean
    var deferredQuery: String?
}

@ContributesBinding(
    scope = AppScope::class,
    binding = binding<DataClearer>(),
)
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<BrowserLifecycleObserver>(),
)
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<ActivityLifecycleCallbacks>(),
)
@SingleIn(AppScope::class)
class AutomaticDataClearer @Inject constructor(
    private val workManager: WorkManager,
    private val settingsDataStore: SettingsDataStore,
    private val clearDataAction: ClearDataAction,
    private val dataClearing: AutomaticDataClearing,
    private val dataClearerForegroundAppRestartPixel: DataClearerForegroundAppRestartPixel,
    private val dispatchers: DispatcherProvider,
    private val fireDataStore: FireDataStore,
    private val dataClearingWideEvent: DataClearingWideEvent,
    private val customTabsFeature: CustomTabsFeature,
) : DataClearer, BrowserLifecycleObserver, ActivityLifecycleCallbacks, CoroutineScope {

    private val clearJob: Job = Job()

    override val coroutineContext: CoroutineContext
        get() = dispatchers.main() + clearJob

    override val dataClearerState: MutableLiveData<ApplicationClearDataState> = MutableLiveData<ApplicationClearDataState>().also {
        it.postValue(INITIALIZING)
    }

    override var isFreshAppLaunch = true

    // Reopened after the restart so the link isn't lost. Reset after every clear.
    @Volatile
    override var deferredQuery: String? = null

    // Opening a custom tab from another app must not run the automatic clear, because the clear would close that tab.
    // The clear waits until the user opens the browser itself. To tell the two apart, these count what is open:
    // custom tabs, the activity that routes links into a custom tab, and everything else.
    private var startedCustomTabActivities = 0
    private var startedOtherActivities = 0
    private var liveIntentDispatchers = 0
    private var isAppOpenPending = false

    override fun onOpen(isFreshLaunch: Boolean) {
        isFreshAppLaunch = isFreshLaunch || (isAppOpenPending && isFreshAppLaunch)
        isAppOpenPending = true
        handleAppOpen()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is IntentDispatcherActivity) liveIntentDispatchers++
    }

    override fun onActivityStarted(activity: Activity) {
        when (activity) {
            is CustomTabActivity -> startedCustomTabActivities++
            is IntentDispatcherActivity -> Unit
            else -> {
                startedOtherActivities++
                handleAppOpenIfBrowserStarted()
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        when (activity) {
            is CustomTabActivity -> {
                startedCustomTabActivities--
                if (!activity.isChangingConfigurations) handleAppOpenIfBrowserStarted()
            }
            is IntentDispatcherActivity -> Unit
            else -> startedOtherActivities--
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is IntentDispatcherActivity) {
            liveIntentDispatchers--
            handleAppOpenIfBrowserStarted()
        }
    }

    // The pending clear only runs once a browser activity is open.
    private fun handleAppOpenIfBrowserStarted() {
        if (isAppOpenPending && startedOtherActivities > 0) handleAppOpen()
    }

    private fun handleAppOpen() {
        // Launched so the counters are up to date when this runs.
        launch {
            if (!isAppOpenPending) return@launch
            if (isCustomTabInFront()) {
                deferClearWhileInCustomTab()
            } else {
                isAppOpenPending = false
                onBrowserOpenedAsync()
            }
        }
    }

    private fun isCustomTabInFront() = customTabsFeature.keepCustomTabOnAppSwitch().isEnabled() &&
        (startedCustomTabActivities > 0 || liveIntentDispatchers > 0)

    private suspend fun deferClearWhileInCustomTab() {
        workManager.cancelAllWorkByTag(DataClearingWorker.WORK_REQUEST_TAG)
        withContext(dispatchers.io()) { settingsDataStore.appUsedSinceLastClear = true }
    }

    @VisibleForTesting
    suspend fun onBrowserOpenedAsync() {
        postDataClearerState(INITIALIZING)

        logcat { "onBrowserOpened; is from fresh app launch? $isFreshAppLaunch" }

        workManager.cancelAllWorkByTag(DataClearingWorker.WORK_REQUEST_TAG)

        withContext(dispatchers.io()) {
            val appUsedSinceLastClear = settingsDataStore.appUsedSinceLastClear
            settingsDataStore.appUsedSinceLastClear = true

            val appIconChanged = settingsDataStore.appIconChanged
            settingsDataStore.appIconChanged = false

            clearDataIfNeeded(appUsedSinceLastClear, appIconChanged)
            deferredQuery = null

            isFreshAppLaunch = false
            settingsDataStore.clearAppBackgroundTimestamp()
        }
    }

    private suspend fun clearDataIfNeeded(
        appUsedSinceLastClear: Boolean,
        appIconChanged: Boolean,
    ) {
        if (dataClearing.shouldClearDataAutomatically(isFreshAppLaunch, appUsedSinceLastClear, appIconChanged)) {
            logcat { "Decided data should be cleared" }
            clearDataWhenAppInForeground()
        } else {
            logcat { "Decided not to clear data at this time" }
            postDataClearerState(FINISHED)
        }
    }

    private suspend fun postDataClearerState(state: ApplicationClearDataState) {
        withContext(dispatchers.main()) {
            dataClearerState.value = state
        }
    }

    override fun onClose() {
        val isClearPending = isAppOpenPending
        launch {
            val timeNow = SystemClock.elapsedRealtime()
            logcat { "Recording when app backgrounded ($timeNow)" }

            postDataClearerState(INITIALIZING)

            withContext(dispatchers.io()) {
                // Keep the original time so the pending clear still runs.
                if (!isClearPending) settingsDataStore.appBackgroundedTimestamp = timeNow

                val clearWhenOption = fireDataStore.getAutomaticallyClearWhenOption()
                val clearOptions = fireDataStore.getAutomaticClearOptions()

                if (clearOptions.isEmpty() || clearWhenOption == ClearWhenOption.APP_EXIT_ONLY) {
                    logcat { "No background timer required for current configuration: $clearOptions / $clearWhenOption" }
                } else {
                    scheduleBackgroundTimerToTriggerClear(clearWhenOption.durationMilliseconds())
                }
            }
        }
    }

    override fun onExit() {
        launch(dispatchers.io()) {
            // the app does not have any activity in CREATED state we kill the process
            val shouldKillProcess = dataClearing.isAutomaticDataClearingOptionSelected()

            if (shouldKillProcess) {
                clearDataAction.killProcess()
            }
        }
    }

    private fun scheduleBackgroundTimerToTriggerClear(durationMillis: Long) {
        workManager.also {
            val workRequest = OneTimeWorkRequestBuilder<DataClearingWorker>()
                .setInitialDelay(durationMillis, TimeUnit.MILLISECONDS)
                .addTag(DataClearingWorker.WORK_REQUEST_TAG)
                .build()
            it.enqueue(workRequest)
            logcat {
                "Work request scheduled, ${durationMillis}ms from now, " +
                    "to clear data if the user hasn't returned to the app. job id: ${workRequest.id}"
            }
        }
    }

    private suspend fun clearDataWhenAppInForeground() {
        withContext(dispatchers.main()) {
            logcat { "Clearing data automatically in foreground with new flow" }

            val clearOptions = fireDataStore.getAutomaticClearOptions()
            dataClearingWideEvent.start(
                entryPoint = DataClearingWideEvent.EntryPoint.AUTO_FOREGROUND,
                clearOptions = clearOptions,
                browserMode = BrowserMode.REGULAR,
            )
            val shouldRestart: Boolean
            try {
                shouldRestart = dataClearing.clearDataUsingAutomaticFireOptions(killProcessIfNeeded = false)
                dataClearingWideEvent.finishSuccess()
            } catch (e: Exception) {
                dataClearingWideEvent.finishFailure(e)
                throw e
            }
            val needsRestart = !isFreshAppLaunch && shouldRestart
            if (needsRestart) {
                withContext(dispatchers.io()) {
                    clearDataAction.setAppUsedSinceLastClearFlag(false)
                    dataClearerForegroundAppRestartPixel.incrementCount()
                }

                val query = deferredQuery
                // need a moment to draw background color (reduces flickering UX)
                Handler(Looper.getMainLooper()).postDelayed(100) {
                    clearDataAction.killAndRestartProcess(notifyDataCleared = true, query = query)
                }
            } else {
                postDataClearerState(FINISHED)
            }
        }
    }
}
