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

package com.duckduckgo.pir.internal.service

import android.content.Context
import android.os.Process
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.scan.PirScan
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import logcat.logcat

@ContributesWorker(AppScope::class)
@SingleInstanceIn(AppScope::class)
class PirScheduledScanRemoteWorker(
    private val context: Context,
    workerParameters: WorkerParameters,
) : RemoteCoroutineWorker(context, workerParameters) {
    @Inject
    lateinit var pirScan: PirScan

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private var shouldComplete: Boolean = false

    override suspend fun doRemoteWork(): Result {
        logcat { "PIR-WORKER ($this}: doRemoteWork ${Process.myPid()}" }
        withContext(dispatcherProvider.main()) {
            pirScan.execute(supportedBrokers, context) {
                shouldComplete = true
            }
        }

        return withContext(dispatcherProvider.io()) {
            while (!shouldComplete) {
                delay(30000)
            }
            logcat { "PIR-WORKER: Completed remote work ${Process.myPid()}" }
            Result.success()
        }
    }

    companion object {
        const val TAG_SCHEDULED_SCAN = "TAG-PIR-SCHEDULED-SCAN"
    }
}
