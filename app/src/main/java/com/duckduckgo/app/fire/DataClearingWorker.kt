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
import androidx.annotation.WorkerThread
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment
import com.duckduckgo.app.settings.db.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber


class DataClearingWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams), CoroutineScope {

    lateinit var settingsDataStore: SettingsDataStore
    lateinit var clearDataAction: ClearDataAction

    @WorkerThread
    override suspend fun doWork(): Payload {

        if (jobAlreadyExecuted()) {
            Timber.i("This job has run before; no more work needed")
            return Payload(Result.SUCCESS)
        }

        settingsDataStore.lastExecutedJobId = id.toString()

        launch {
            clearData(settingsDataStore.automaticallyClearWhatOption)
        }.join()

        Timber.i("Returning success")
        return Payload(Result.SUCCESS)
    }

    private fun jobAlreadyExecuted(): Boolean {
        val newJobId = id.toString()
        val lastJobId = settingsDataStore.lastExecutedJobId
        Timber.i("Worker invoked - new jobId: $newJobId, last jobId: $lastJobId")
        return lastJobId == newJobId
    }

    suspend fun clearData(clearWhat: SettingsAutomaticallyClearWhatFragment.ClearWhatOption) {
        Timber.i("Clearing data: $clearWhat")

        when (clearWhat) {
            SettingsAutomaticallyClearWhatFragment.ClearWhatOption.CLEAR_NONE -> Timber.w("Automatically clear data invoked, but set to clear nothing")
            SettingsAutomaticallyClearWhatFragment.ClearWhatOption.CLEAR_TABS_ONLY -> clearTabsOnly()
            SettingsAutomaticallyClearWhatFragment.ClearWhatOption.CLEAR_TABS_AND_DATA -> clearEverything()

        }
    }

    private suspend fun clearTabsOnly() {
        launch(Dispatchers.IO) {
            clearDataAction.clearTabsAsync(appInForeground = false)
        }.join()
    }


    private suspend fun clearEverything() {
        Timber.i("App is in background, so just outright killing the process")
        launch(Dispatchers.Main) {
            clearDataAction.clearTabsAndAllDataAsync(appInForeground = false)
        }.join()

        Timber.i("Will kill process now: jobId: $id")
        clearDataAction.killProcess()
    }

    companion object {
        const val WORK_REQUEST_TAG = "background-clear-data"
    }
}

