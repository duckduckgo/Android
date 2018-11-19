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
import androidx.core.os.postDelayed
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment.ClearWhatOption
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhenFragment.ClearWhenOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit


class AutomaticDataClearer(
    private val settingsDataStore: SettingsDataStore,
    private val clearDataAction: ClearDataAction,
    private val dataClearerTimeKeeper: BackgroundTimeKeeper) : LifecycleObserver {

    private var isFreshAppLaunch = false

    private var backgroundJobId: UUID? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onAppCreated() {
        isFreshAppLaunch = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {

        Timber.i("onAppForegrounded; is from fresh app launch? $isFreshAppLaunch")

        backgroundJobId?.let {
            Timber.i("Cancelling background job with ID $it")
            WorkManager.getInstance().cancelWorkById(it)
        }

        val clearWhat = settingsDataStore.automaticallyClearWhatOption
        val clearWhen = settingsDataStore.automaticallyClearWhenOption

        Timber.d("Currently configured to automatically clear $clearWhat")
        if (clearWhat != ClearWhatOption.CLEAR_NONE) {
            if (shouldClearData(clearWhen)) {
                clearData(clearWhat, appInBackground = false)
            } else {
                Timber.d("Will not clear data at this time")
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
            Timber.i("No background timer required for current configuration: $clearWhatOption / $clearWhenOption")
        } else {
            scheduleBackgroundTimerToTriggerClear(clearWhenOption.durationMillis)
        }
    }

    private fun scheduleBackgroundTimerToTriggerClear(durationMillis: Long) {
        Timber.i("Scheduling background timer, ${durationMillis}ms from now, to clear data if the user hasn't returned to the app")

        WorkManager.getInstance().also {
            val workRequest = OneTimeWorkRequestBuilder<DataClearingWorker>()
                .setInitialDelay(durationMillis, TimeUnit.MILLISECONDS)
                .build()
            backgroundJobId = workRequest.id
            it.enqueue(workRequest)
        }
    }

    fun clearData(clearWhat: ClearWhatOption, appInBackground: Boolean) {
        Timber.i("Clearing data: $clearWhat")

        when (clearWhat) {
            ClearWhatOption.CLEAR_NONE -> Timber.w("Automatically clear data invoked, but set to clear nothing")
            ClearWhatOption.CLEAR_TABS_ONLY -> {
                clearDataAction.clearTabs()
            }
            ClearWhatOption.CLEAR_TABS_AND_DATA -> {
                if (appInBackground) {
                    Timber.w("App is in background, so just outright killing it")
                    clearDataAction.clearEverything(killProcess = true)
                } else {
                    val processNeedsRestarted = !isFreshAppLaunch
                    Timber.i("App is in foreground; restart needed? $processNeedsRestarted")

                    Handler().postDelayed(300) {
                        Timber.i("Clearing now")
                        clearDataAction.clearEverything(killAndRestartProcess = processNeedsRestarted)
                    }
                }
            }
        }
    }

    private fun shouldClearData(cleanWhenOption: ClearWhenOption): Boolean {
        return false
        if (isFreshAppLaunch && timeSinceLastClearEnough()) return true
        if (cleanWhenOption == ClearWhenOption.APP_EXIT_ONLY) return false
        return dataClearerTimeKeeper.hasEnoughTimeElapsed()

    }

    private fun timeSinceLastClearEnough(): Boolean {
        if (!settingsDataStore.hasLastClearTimestamp()) {
            Timber.d("Not last clear timestamp available")
            return true
        }
        val lastClear = settingsDataStore.lastClearTimestamp
        val lastClearDuration = (System.currentTimeMillis() - lastClear)
        Timber.d("Last cleared ${lastClearDuration}ms ago")
        return lastClearDuration >= TimeUnit.SECONDS.toMillis(5)
    }
}