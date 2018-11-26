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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment.ClearWhatOption
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhenFragment.ClearWhenOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

interface DataClearListener {
    fun onNoClearRequired()
    fun onClearFinished()
}

class AutomaticDataClearer(
    private val settingsDataStore: SettingsDataStore,
    private val clearDataAction: ClearDataAction,
    private val dataClearerTimeKeeper: BackgroundTimeKeeper
) : LifecycleObserver, CoroutineScope {

    private val clearJob: Job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + clearJob

    var isFreshAppLaunch = true
    var listener: DataClearListener? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {

        Timber.i("onAppForegrounded; is from fresh app launch? $isFreshAppLaunch")

        WorkManager.getInstance().cancelAllWorkByTag(DataClearingWorker.WORK_REQUEST_TAG)

        val appUsedSinceLastClear = settingsDataStore.appUsedSinceLastClear
        settingsDataStore.appUsedSinceLastClear = true

        val clearWhat = settingsDataStore.automaticallyClearWhatOption
        val clearWhen = settingsDataStore.automaticallyClearWhenOption
        Timber.i("Currently configured to automatically clear $clearWhat / $clearWhen")

        if (clearWhat == ClearWhatOption.CLEAR_NONE) {
            Timber.i("No data will be cleared as it's configured to clear nothing automatically")
            listener?.onNoClearRequired()
        } else {
            if (shouldClearData(clearWhen, appUsedSinceLastClear)) {
                Timber.i("Decided data should be cleared")
                clearDataWhenAppInForeground(clearWhat)
            } else {
                Timber.i("Decided not to clear data at this time")
                listener?.onNoClearRequired()
            }
        }

        isFreshAppLaunch = false
        settingsDataStore.clearAppBackgroundTimestamp()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        Timber.i("Recording when app backgrounded")
        settingsDataStore.appBackgroundedTimestamp = System.currentTimeMillis()

        val clearWhenOption = settingsDataStore.automaticallyClearWhenOption
        val clearWhatOption = settingsDataStore.automaticallyClearWhatOption

        if (clearWhatOption == ClearWhatOption.CLEAR_NONE || clearWhenOption == ClearWhenOption.APP_EXIT_ONLY) {
            Timber.d("No background timer required for current configuration: $clearWhatOption / $clearWhenOption")
        } else {
            scheduleBackgroundTimerToTriggerClear(clearWhenOption.durationMillis)
        }
    }

    private fun scheduleBackgroundTimerToTriggerClear(durationMillis: Long) {
        WorkManager.getInstance().also {
            val workRequest = OneTimeWorkRequestBuilder<DataClearingWorker>()
                .setInitialDelay(durationMillis, TimeUnit.MILLISECONDS)
                .addTag(DataClearingWorker.WORK_REQUEST_TAG)
                .build()
            it.enqueue(workRequest)
            Timber.i("Work request scheduled, ${durationMillis}ms from now, to clear data if the user hasn't returned to the app. job id: ${workRequest.id}")
        }
    }

    @Suppress("NON_EXHAUSTIVE_WHEN")
    private fun clearDataWhenAppInForeground(clearWhat: ClearWhatOption) {
        Timber.i("Clearing data when app is in the foreground: $clearWhat")

        when (clearWhat) {
            ClearWhatOption.CLEAR_TABS_ONLY -> {
                launch(Dispatchers.IO) {
                    clearDataAction.clearTabsAsync(true)

                    withContext(Dispatchers.Main) {
                        Timber.i("Notifying listener that clearing has finished")
                        listener?.onClearFinished()

                    }
                }
            }

            ClearWhatOption.CLEAR_TABS_AND_DATA -> {
                val processNeedsRestarted = !isFreshAppLaunch
                Timber.i("App is in foreground; restart needed? $processNeedsRestarted")

                launch(Dispatchers.Main) {
                    clearDataAction.clearTabsAndAllDataAsync(appInForeground = true)
                    Timber.i("Notifying listener that clearing has finished")
                    listener?.onClearFinished()

                    Timber.i("All data now cleared, will restart process? $processNeedsRestarted")
                    if (processNeedsRestarted) {
                        Timber.i("Will now restart process")
                        clearDataAction.killAndRestartProcess()
                    } else {
                        Timber.i("Will not restart process")
                    }
                }
            }
        }
    }

    private fun shouldClearData(cleanWhenOption: ClearWhenOption, appUsedSinceLastClear: Boolean): Boolean {
        Timber.d("Determining if data should be cleared for option $cleanWhenOption")

        if (!appUsedSinceLastClear) {
            Timber.d("App hasn't been used since last clear; no need to clear again")
            return false
        }
        Timber.d("App has been used since last clear")

        if (isFreshAppLaunch) {
            Timber.d("This is a fresh app launch, so will clear the data")
            return true
        }
        if (cleanWhenOption == ClearWhenOption.APP_EXIT_ONLY) {
            Timber.d("This is NOT a fresh app launch, and the configuration is for app exit only. Not clearing the data")
            return false
        }

        val enoughTimePassed = dataClearerTimeKeeper.hasEnoughTimeElapsed()
        Timber.d("Has enough time passed to trigger the data clear? $enoughTimePassed")
        return enoughTimePassed
    }
}