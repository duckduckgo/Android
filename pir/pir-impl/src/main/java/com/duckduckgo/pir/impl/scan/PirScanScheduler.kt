/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.scan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.multiprocess.RemoteListenableWorker
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.PirJobConstants.SCHEDULED_SCAN_INTERVAL_HOURS
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.scan.PirScheduledScanRemoteWorker.Companion.TAG_SCHEDULED_SCAN
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.db.EventType
import com.duckduckgo.pir.impl.store.db.PirEventLog
import com.squareup.anvil.annotations.ContributesBinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

interface PirScanScheduler {
    fun scheduleScans()
    fun cancelScheduledScans(context: Context)
}

@ContributesBinding(AppScope::class)
class RealPirScanScheduler @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val workManager: WorkManager,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pirPixelSender: PirPixelSender,
    private val eventsRepository: PirEventsRepository,
) : PirScanScheduler {
    override fun scheduleScans() {
        logcat { "PIR-SCHEDULED: Scheduling periodic scan appId: ${appBuildConfig.applicationId}" }

        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest =
            PeriodicWorkRequest.Builder(
                PirScheduledScanRemoteWorker::class.java,
                SCHEDULED_SCAN_INTERVAL_HOURS,
                TimeUnit.HOURS,
            )
                .boundToPirProcess(appBuildConfig.applicationId)
                .setConstraints(constraints)
                .build()

        pirPixelSender.reportScheduledScanScheduled()
        coroutineScope.launch {
            eventsRepository.saveScanLog(
                PirEventLog(
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    eventType = EventType.SCHEDULED_SCAN_SCHEDULED,
                ),
            )
        }

        workManager.enqueueUniquePeriodicWork(
            TAG_SCHEDULED_SCAN,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest,
        )
    }

    override fun cancelScheduledScans(context: Context) {
        workManager.cancelUniqueWork(TAG_SCHEDULED_SCAN)
        context.stopService(Intent(context, PirRemoteWorkerService::class.java))
    }

    private fun PeriodicWorkRequest.Builder.boundToPirProcess(applicationId: String): PeriodicWorkRequest.Builder {
        val componentName = ComponentName(applicationId, PirRemoteWorkerService::class.java.name)
        val data = Data.Builder()
            .putString(RemoteListenableWorker.ARGUMENT_PACKAGE_NAME, componentName.packageName)
            .putString(RemoteListenableWorker.ARGUMENT_CLASS_NAME, componentName.className)
            .build()

        return this.setInputData(data)
    }
}
