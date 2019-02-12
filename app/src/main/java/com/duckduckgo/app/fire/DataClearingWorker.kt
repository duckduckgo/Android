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
import androidx.work.ListenableWorker.Result.success
import androidx.work.WorkerParameters
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


class DataClearingWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams), CoroutineScope {

    lateinit var settingsDataStore: SettingsDataStore
    lateinit var clearDataAction: ClearDataAction

    @WorkerThread
    override suspend fun doWork(): Result {

        if (jobAlreadyExecuted()) {
            Timber.i("This job has run before; no more work needed")
            return success()
        }

        settingsDataStore.lastExecutedJobId = id.toString()

        clearData(settingsDataStore.automaticallyClearWhatOption)

        Timber.i("Clear data job finished; returning SUCCESS")
        return success()
    }

    /**
     * If we are killing the process as part of running the job, WorkManager will not be aware that this task finished successfully.
     * As such, it will try and run it again soon.
     *
     * We store the last job ID internally so that we can bail early if we've executed it before. This time, WorkManager will mark it as successful.
     */
    private fun jobAlreadyExecuted(): Boolean {
        val newJobId = id.toString()
        val lastJobId = settingsDataStore.lastExecutedJobId
        return lastJobId == newJobId
    }

    suspend fun clearData(clearWhat: ClearWhatOption) {
        Timber.i("Clearing data: $clearWhat")

        when (clearWhat) {
            ClearWhatOption.CLEAR_NONE -> Timber.w("Automatically clear data invoked, but set to clear nothing")
            ClearWhatOption.CLEAR_TABS_ONLY -> clearDataAction.clearTabsAsync(appInForeground = false)
            ClearWhatOption.CLEAR_TABS_AND_DATA -> clearEverything()
        }
    }

    private suspend fun clearEverything() {
        Timber.i("App is in background, so just outright killing the process")
        withContext(Dispatchers.Main) {
            clearDataAction.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
            clearDataAction.setAppUsedSinceLastClearFlag(false)

            Timber.i("Will kill process now")
            clearDataAction.killProcess()
        }
    }

    companion object {
        const val WORK_REQUEST_TAG = "background-clear-data"
    }
}

