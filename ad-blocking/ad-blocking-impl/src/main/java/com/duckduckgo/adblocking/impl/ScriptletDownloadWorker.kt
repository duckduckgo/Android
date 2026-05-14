/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.duckduckgo.adblocking.impl.domain.ScriptletUpdateResult
import com.duckduckgo.adblocking.impl.domain.ScriptletUpdater
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionConfigProvider
import com.duckduckgo.adblocking.impl.remoteconfig.ScriptletsSettings
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class ScriptletDownloadWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {

    @Inject
    lateinit var updater: ScriptletUpdater

    @Inject
    lateinit var settingsAdapter: JsonAdapter<ScriptletsSettings>

    @Inject
    lateinit var dispatchers: DispatcherProvider

    override suspend fun doWork(): Result = withContext(dispatchers.io()) {
        logcat { "Starting ScriptletDownloadWorker" }
        val settingsJson = inputData.getString(KEY_SETTINGS) ?: return@withContext Result.failure()
        val settings = runCatching { settingsAdapter.fromJson(settingsJson) }
            .onFailure { logcat(WARN) { "ScriptletDownloadWorker: failed to parse settings input: ${it.asLog()}" } }
            .getOrNull()
            ?: return@withContext Result.failure()

        when (updater.update(settings)) {
            ScriptletUpdateResult.Success -> Result.success()
            ScriptletUpdateResult.Retry -> Result.retry()
        }
    }

    companion object {
        const val KEY_SETTINGS = "settings"
    }
}

@ContributesMultibinding(scope = AppScope::class, boundType = MainProcessLifecycleObserver::class)
@SingleInstanceIn(AppScope::class)
class ScriptletDownloadWorkerScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val configProvider: AdBlockingExtensionConfigProvider,
    private val settingsAdapter: JsonAdapter<ScriptletsSettings>,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appScope.launch {
            configProvider.scriptletsSettings.filterNotNull().collect { settings ->
                enqueue(settings)
            }
        }
    }

    private fun enqueue(settings: ScriptletsSettings) {
        val json = settingsAdapter.toJson(settings)
        val request = OneTimeWorkRequestBuilder<ScriptletDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INITIAL_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(ScriptletDownloadWorker.KEY_SETTINGS to json))
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WORK_NAME = "AdBlockingExtensionScriptletDownloadWorker"
        private const val BACKOFF_INITIAL_SECONDS = 30L
    }
}
