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
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment.ClearWhatOption
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhenFragment.ClearWhenOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import timber.log.Timber
import javax.inject.Inject


class AutomaticDataClearer @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val clearDataAction: ClearDataAction,
    private val dataClearerTimeKeeper: BackgroundTimeKeeper
) : LifecycleObserver {

    private var isFreshAppLaunch = false


    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onAppCreated() {
        isFreshAppLaunch = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        Timber.i("onAppForegrounded; is from fresh app launch? $isFreshAppLaunch")

        val clearWhat = settingsDataStore.automaticallyClearWhatOption
        val clearWhen = settingsDataStore.automaticallyClearWhenOption

        Timber.d("Currently configured to automatically clear $clearWhat")
        if (clearWhat != ClearWhatOption.CLEAR_NONE) {
            if (shouldClearData(clearWhen)) {
                clearData(clearWhat, restartProcess = !isFreshAppLaunch)
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
    }

    fun clearData(clearWhat: ClearWhatOption, restartProcess: Boolean) {
        Timber.i("Clearing data: $clearWhat")

        when (clearWhat) {
            ClearWhatOption.CLEAR_NONE -> Timber.w("Automatically clear data invoked, but set to clear nothing")
            ClearWhatOption.CLEAR_TABS_ONLY -> clearDataAction.clearTabs()
            ClearWhatOption.CLEAR_TABS_AND_DATA -> clearDataAction.clearEverything(restartProcess)
        }
    }

    private fun shouldClearData(cleanWhenOption: ClearWhenOption): Boolean {
        if (isFreshAppLaunch) return true

        if (cleanWhenOption == ClearWhenOption.APP_EXIT_ONLY) return false

        return dataClearerTimeKeeper.hasEnoughTimeElapsed()

    }

}