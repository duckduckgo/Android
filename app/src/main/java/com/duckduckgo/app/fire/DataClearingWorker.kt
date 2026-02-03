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
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class DataClearingWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams), CoroutineScope {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var clearDataAction: ClearDataAction

    @Inject
    lateinit var dataClearing: AutomaticDataClearing

    @Inject
    lateinit var androidBrowserConfigFeature: AndroidBrowserConfigFeature

    @Inject
    lateinit var fireDataStore: FireDataStore

    @Inject
    lateinit var dataClearingWideEvent: DataClearingWideEvent

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @WorkerThread
    override suspend fun doWork(): Result {
        if (jobAlreadyExecuted()) {
            logcat(INFO) { "This job has run before; no more work needed" }
            return success()
        }

        settingsDataStore.lastExecutedJobId = id.toString()

        withContext(dispatchers.io()) {
            if (androidBrowserConfigFeature.improvedDataClearingOptions().isEnabled()) {
                // Use new granular clearing - will automatically kill the process
                val clearOptions = fireDataStore.getAutomaticClearOptions()
                dataClearingWideEvent.start(
                    entryPoint = DataClearingWideEvent.EntryPoint.AUTO_BACKGROUND,
                    clearOptions = clearOptions,
                )
                try {
                    dataClearing.clearDataUsingAutomaticFireOptions()
                    dataClearingWideEvent.finishSuccess()
                } catch (e: Exception) {
                    dataClearingWideEvent.finishFailure(e)
                    throw e
                }
            } else {
                val clearWhatOption = settingsDataStore.automaticallyClearWhatOption
                dataClearingWideEvent.startLegacy(
                    entryPoint = DataClearingWideEvent.EntryPoint.LEGACY_AUTO_BACKGROUND,
                    clearWhatOption = clearWhatOption,
                    clearDuckAiData = settingsDataStore.clearDuckAiData,
                )
                try {
                    clearData(clearWhatOption)
                    dataClearingWideEvent.finishSuccess()
                } catch (e: Exception) {
                    dataClearingWideEvent.finishFailure(e)
                    throw e
                }
                if (clearWhatOption == ClearWhatOption.CLEAR_TABS_AND_DATA) {
                    logcat(INFO) { "Will kill process now" }
                    clearDataAction.killProcess()
                }
            }
        }

        logcat(INFO) { "Clear data job finished; returning SUCCESS" }
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
        logcat(INFO) { "Clearing data: $clearWhat" }

        when (clearWhat) {
            ClearWhatOption.CLEAR_NONE -> logcat(WARN) { "Automatically clear data invoked, but set to clear nothing" }
            ClearWhatOption.CLEAR_TABS_ONLY -> clearDataAction.clearTabsAsync(appInForeground = false)
            ClearWhatOption.CLEAR_TABS_AND_DATA -> clearEverything()
        }
    }

    private suspend fun clearEverything() {
        withContext(dispatchers.main()) {
            // Use legacy clearing
            clearDataAction.clearTabsAndAllDataAsync(appInForeground = false, shouldFireDataClearPixel = false)
            clearDataAction.setAppUsedSinceLastClearFlag(false)
        }
    }

    companion object {
        const val WORK_REQUEST_TAG = "background-clear-data"
    }
}
