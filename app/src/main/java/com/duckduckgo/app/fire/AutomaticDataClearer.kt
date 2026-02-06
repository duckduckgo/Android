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

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.core.os.postDelayed
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.global.ApplicationClearDataState
import com.duckduckgo.app.global.ApplicationClearDataState.FINISHED
import com.duckduckgo.app.global.ApplicationClearDataState.INITIALIZING
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

interface DataClearer {
    val dataClearerState: LiveData<ApplicationClearDataState>
    var isFreshAppLaunch: Boolean
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = DataClearer::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = BrowserLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class AutomaticDataClearer @Inject constructor(
    private val workManager: WorkManager,
    private val settingsDataStore: SettingsDataStore,
    private val clearDataAction: ClearDataAction,
    private val dataClearing: AutomaticDataClearing,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val dataClearerTimeKeeper: BackgroundTimeKeeper,
    private val dataClearerForegroundAppRestartPixel: DataClearerForegroundAppRestartPixel,
    private val dispatchers: DispatcherProvider,
    private val fireDataStore: FireDataStore,
    private val dataClearingWideEvent: DataClearingWideEvent,
) : DataClearer, BrowserLifecycleObserver, CoroutineScope {

    private val clearJob: Job = Job()

    override val coroutineContext: CoroutineContext
        get() = dispatchers.main() + clearJob

    override val dataClearerState: MutableLiveData<ApplicationClearDataState> = MutableLiveData<ApplicationClearDataState>().also {
        it.postValue(INITIALIZING)
    }

    override var isFreshAppLaunch = true

    override fun onOpen(isFreshLaunch: Boolean) {
        isFreshAppLaunch = isFreshLaunch
        launch {
            onAppForegroundedAsync()
        }
    }

    @VisibleForTesting
    suspend fun onAppForegroundedAsync() {
        postDataClearerState(INITIALIZING)

        logcat { "onAppForegrounded; is from fresh app launch? $isFreshAppLaunch" }

        workManager.cancelAllWorkByTag(DataClearingWorker.WORK_REQUEST_TAG)

        withContext(dispatchers.io()) {
            val appUsedSinceLastClear = settingsDataStore.appUsedSinceLastClear
            settingsDataStore.appUsedSinceLastClear = true

            val appIconChanged = settingsDataStore.appIconChanged
            settingsDataStore.appIconChanged = false

            if (androidBrowserConfigFeature.improvedDataClearingOptions().isEnabled()) {
                clearDataIfNeeded(appUsedSinceLastClear, appIconChanged)
            } else {
                clearDataIfNeededLegacy(appUsedSinceLastClear, appIconChanged)
            }

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

    private suspend fun clearDataIfNeededLegacy(
        appUsedSinceLastClear: Boolean,
        appIconChanged: Boolean,
    ) {
        val clearWhat = settingsDataStore.automaticallyClearWhatOption
        val clearWhen = settingsDataStore.automaticallyClearWhenOption
        logcat { "Currently configured to automatically clear $clearWhat / $clearWhen" }

        if (clearWhat == ClearWhatOption.CLEAR_NONE) {
            logcat { "No data will be cleared as it's configured to clear nothing automatically" }
            postDataClearerState(FINISHED)
        } else {
            if (shouldClearData(clearWhen, appUsedSinceLastClear, appIconChanged)) {
                logcat { "Decided data should be cleared" }
                clearDataWhenAppInForegroundLegacy(clearWhat)
            } else {
                logcat { "Decided not to clear data at this time" }
                postDataClearerState(FINISHED)
            }
        }
    }

    private suspend fun postDataClearerState(state: ApplicationClearDataState) {
        withContext(dispatchers.main()) {
            dataClearerState.value = state
        }
    }

    override fun onClose() {
        launch {
            val timeNow = SystemClock.elapsedRealtime()
            logcat { "Recording when app backgrounded ($timeNow)" }

            postDataClearerState(INITIALIZING)

            withContext(dispatchers.io()) {
                settingsDataStore.appBackgroundedTimestamp = timeNow

                if (androidBrowserConfigFeature.improvedDataClearingOptions().isEnabled()) {
                    val clearWhenOption = fireDataStore.getAutomaticallyClearWhenOption()
                    val clearOptions = fireDataStore.getAutomaticClearOptions()

                    if (clearOptions.isEmpty() || clearWhenOption == ClearWhenOption.APP_EXIT_ONLY) {
                        logcat { "No background timer required for current configuration: $clearOptions / $clearWhenOption" }
                    } else {
                        scheduleBackgroundTimerToTriggerClear(clearWhenOption.durationMilliseconds())
                    }
                } else {
                    val clearWhenOption = settingsDataStore.automaticallyClearWhenOption
                    val clearWhatOption = settingsDataStore.automaticallyClearWhatOption

                    if (clearWhatOption == ClearWhatOption.CLEAR_NONE || clearWhenOption == ClearWhenOption.APP_EXIT_ONLY) {
                        logcat { "No background timer required for current configuration: $clearWhatOption / $clearWhenOption" }
                    } else {
                        scheduleBackgroundTimerToTriggerClear(clearWhenOption.durationMilliseconds())
                    }
                }
            }
        }
    }

    override fun onExit() {
        launch(dispatchers.io()) {
            // the app does not have any activity in CREATED state we kill the process
            val shouldKillProcess = if (androidBrowserConfigFeature.improvedDataClearingOptions().isEnabled()) {
                dataClearing.isAutomaticDataClearingOptionSelected()
            } else {
                settingsDataStore.automaticallyClearWhatOption != ClearWhatOption.CLEAR_NONE
            }

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

                // need a moment to draw background color (reduces flickering UX)
                Handler(Looper.getMainLooper()).postDelayed(100) {
                    clearDataAction.killAndRestartProcess(notifyDataCleared = true)
                }
            } else {
                postDataClearerState(FINISHED)
            }
        }
    }

    @UiThread
    @Suppress("NON_EXHAUSTIVE_WHEN")
    private suspend fun clearDataWhenAppInForegroundLegacy(clearWhat: ClearWhatOption) {
        withContext(dispatchers.main()) {
            logcat { "Clearing data when app is in the foreground: $clearWhat" }

            dataClearingWideEvent.startLegacy(
                entryPoint = DataClearingWideEvent.EntryPoint.LEGACY_AUTO_FOREGROUND,
                clearWhatOption = clearWhat,
                clearDuckAiData = settingsDataStore.clearDuckAiData,
            )
            // Use legacy clearing
            val processNeedsRestarted = !isFreshAppLaunch && clearWhat == ClearWhatOption.CLEAR_TABS_AND_DATA
            logcat { "App is in foreground; restart needed? $processNeedsRestarted" }

            when (clearWhat) {
                ClearWhatOption.CLEAR_TABS_ONLY -> {
                    try {
                        clearDataAction.clearTabsAsync(true)
                        dataClearingWideEvent.finishSuccess()
                    } catch (e: Exception) {
                        dataClearingWideEvent.finishFailure(e)
                        throw e
                    }

                    logcat { "Notifying listener that clearing has finished" }
                    postDataClearerState(FINISHED)
                }

                ClearWhatOption.CLEAR_TABS_AND_DATA -> {
                    try {
                        clearDataAction.clearTabsAndAllDataAsync(appInForeground = true, shouldFireDataClearPixel = false)
                        dataClearingWideEvent.finishSuccess()
                    } catch (e: Exception) {
                        dataClearingWideEvent.finishFailure(e)
                        throw e
                    }

                    logcat { "All data now cleared, will restart process? $processNeedsRestarted" }
                    if (processNeedsRestarted) {
                        withContext(dispatchers.io()) {
                            clearDataAction.setAppUsedSinceLastClearFlag(false)
                            dataClearerForegroundAppRestartPixel.incrementCount()
                        }

                        // need a moment to draw background color (reduces flickering UX)
                        Handler(Looper.getMainLooper()).postDelayed(100) {
                            logcat { "Will now restart process" }
                            clearDataAction.killAndRestartProcess(notifyDataCleared = true)
                        }
                    } else {
                        logcat { "Will not restart process" }
                        postDataClearerState(FINISHED)
                    }
                }

                else -> {}
            }
        }
    }

    private fun shouldClearData(
        cleanWhenOption: ClearWhenOption,
        appUsedSinceLastClear: Boolean,
        appIconChanged: Boolean,
    ): Boolean {
        logcat { "Determining if data should be cleared for option $cleanWhenOption" }

        if (!appUsedSinceLastClear) {
            logcat { "App hasn't been used since last clear; no need to clear again" }
            return false
        }

        logcat { "App has been used since last clear" }

        if (isFreshAppLaunch) {
            logcat { "This is a fresh app launch, so will clear the data" }
            return true
        }

        if (appIconChanged) {
            logcat { "No data will be cleared as the app icon was just changed" }
            return false
        }

        if (cleanWhenOption == ClearWhenOption.APP_EXIT_ONLY) {
            logcat { "This is NOT a fresh app launch, and the configuration is for app exit only. Not clearing the data" }
            return false
        }
        if (!settingsDataStore.hasBackgroundTimestampRecorded()) {
            logcat { "No background timestamp recorded; will not clear the data" }
            logcat(WARN) { "No background timestamp recorded; will not clear the data" }
            return false
        }

        val enoughTimePassed = dataClearerTimeKeeper.hasEnoughTimeElapsed(
            backgroundedTimestamp = settingsDataStore.appBackgroundedTimestamp,
            clearWhenOption = cleanWhenOption,
        )
        logcat { "Has enough time passed to trigger the data clear? $enoughTimePassed" }

        return enoughTimePassed
    }
}
