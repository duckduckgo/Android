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
import androidx.core.os.postDelayed
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment
import com.duckduckgo.app.settings.db.SettingsDataStore
import timber.log.Timber


class DataClearingWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    lateinit var settingsDataStore: SettingsDataStore
    lateinit var clearDataAction: ClearDataAction


    override fun doWork(): Result {
        Timber.i("Doing work")
        clearData(settingsDataStore.automaticallyClearWhatOption, appInBackground = true)
        return Result.SUCCESS
    }

    fun clearData(clearWhat: SettingsAutomaticallyClearWhatFragment.ClearWhatOption, appInBackground: Boolean) {
        Timber.i("Clearing data: $clearWhat")

        when (clearWhat) {
            SettingsAutomaticallyClearWhatFragment.ClearWhatOption.CLEAR_NONE -> Timber.w("Automatically clear data invoked, but set to clear nothing")
            SettingsAutomaticallyClearWhatFragment.ClearWhatOption.CLEAR_TABS_ONLY -> {
                clearDataAction.clearTabs()
            }
            SettingsAutomaticallyClearWhatFragment.ClearWhatOption.CLEAR_TABS_AND_DATA -> {
                if (appInBackground) {
                    Timber.w("App is in background, so just outright killing it")
                    clearDataAction.clearEverything(killProcess = true)
                } else {
                    val processNeedsRestarted = true
                    Timber.i("App is in foreground; restart needed? $processNeedsRestarted")

                    Handler().postDelayed(300) {
                        Timber.i("Clearing now")
                        clearDataAction.clearEverything(killAndRestartProcess = processNeedsRestarted)
                    }
                }
            }
        }
    }


}

class DaggerWorkerFactory(private val settingsDataStore: SettingsDataStore, private val clearDataAction: ClearDataAction) : WorkerFactory() {

    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? {

        val workerKlass = Class.forName(workerClassName).asSubclass(Worker::class.java)
        val constructor = workerKlass.getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)

        val instance = constructor.newInstance(appContext, workerParameters)

        when (instance) {
            is DataClearingWorker -> {
                instance.settingsDataStore = settingsDataStore
                instance.clearDataAction = clearDataAction
            }
            else -> {
                Timber.i("No injection required for worker workerClassName")
            }
        }

        return instance
    }
}