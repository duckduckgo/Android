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

package com.duckduckgo.pir.internal.scan

import android.content.Context
import android.os.Process
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.checker.PirWorkHandler
import com.duckduckgo.pir.internal.common.PirJob.RunType.SCHEDULED
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import logcat.logcat

@ContributesWorker(AppScope::class)
class PirScheduledScanRemoteWorker(
    private val context: Context,
    workerParameters: WorkerParameters,
) : RemoteCoroutineWorker(context, workerParameters) {
    @Inject
    lateinit var pirScan: PirScan

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var pirWorkHandler: PirWorkHandler

    override suspend fun doRemoteWork(): Result {
        logcat { "PIR-WORKER ($this}: doRemoteWork ${Process.myPid()}" }
        return try {
            if (pirWorkHandler.canRunPir().firstOrNull() == false) {
                logcat { "PIR-WORKER ($this}: PIR not allowed to run!" }
                pirWorkHandler.cancelWork()
                return Result.failure()
            }

            val result = pirScan.executeAllBrokers(context.applicationContext, SCHEDULED)

            if (result.isSuccess) {
                logcat { "PIR-WORKER ($this}: Successfully completed!" }
                Result.success()
            } else {
                logcat { "PIR-WORKER ($this}: Failed to complete." }
                Result.failure()
            }
        } catch (_: Exception) {
            // this can happen as a result of scanning error or cancellation because PIR is no longer enabled
            logcat { "PIR-WORKER ($this}: Exception occurred, stopping all work!" }
            pirScan.stop()
            Result.failure()
        }
    }

    companion object {
        internal const val TAG_SCHEDULED_SCAN = "TAG-PIR-SCHEDULED-SCAN"
    }
}
