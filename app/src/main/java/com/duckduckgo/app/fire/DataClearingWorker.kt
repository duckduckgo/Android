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

import android.content.Context
import android.os.Handler
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.os.postDelayed
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment
import com.duckduckgo.app.settings.db.SettingsDataStore
import org.jetbrains.anko.runOnUiThread
import timber.log.Timber


class DataClearingWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    lateinit var settingsDataStore: SettingsDataStore
    lateinit var clearDataAction: ClearDataAction

    @WorkerThread
    override fun doWork(): Result {
        Timber.i("Doing work")
        clearData(settingsDataStore.automaticallyClearWhatOption, appInBackground = true)
        return Result.SUCCESS
    }

    @AnyThread
    fun clearData(clearWhat: SettingsAutomaticallyClearWhatFragment.ClearWhatOption, appInBackground: Boolean) {
        Timber.i("Clearing data: $clearWhat")

        when (clearWhat) {
            SettingsAutomaticallyClearWhatFragment.ClearWhatOption.CLEAR_NONE -> Timber.w("Automatically clear data invoked, but set to clear nothing")
            SettingsAutomaticallyClearWhatFragment.ClearWhatOption.CLEAR_TABS_ONLY -> {
                clearTabs(appInBackground)
            }
            SettingsAutomaticallyClearWhatFragment.ClearWhatOption.CLEAR_TABS_AND_DATA -> {
                applicationContext.runOnUiThread {
                    clearTabsAndData(appInBackground)
                }
            }
        }
    }

    private fun clearTabs(appInBackground: Boolean) {
        clearDataAction.clearTabs(appInForeground = !appInBackground)
    }

    @UiThread
    private fun clearTabsAndData(appInBackground: Boolean) {
        if (appInBackground) {
            Timber.w("App is in background, so just outright killing it")
            clearDataAction.clearTabsAndAllData(killProcess = true, appInForeground = !appInBackground)
        } else {
            val processNeedsRestarted = true
            Timber.i("App is in foreground; ${!appInBackground}. restart needed? $processNeedsRestarted")

            Handler().postDelayed(300) {
                Timber.i("Clearing now")
                clearDataAction.clearTabsAndAllData(killAndRestartProcess = processNeedsRestarted, appInForeground = !appInBackground)
            }
        }
    }
}

