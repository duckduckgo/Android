/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.waitlist

import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.HOURS
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class NetPWaitlistChecker @Inject constructor(
    private val netPWaitlistManager: NetPWaitlistManager,
    private val waitlistNotification: NetPWaitlistCodeNotification,
    private val workManager: WorkManager,
) : MainProcessLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        schedulePeriodicCheck()
    }

    internal suspend fun checkAndUpdateState() {
        netPWaitlistManager.upsertState().also { state ->
            if (state is InBeta) {
                waitlistNotification.sendNotification()
            } else {
                workManager.cancelAllWorkByTag(TAG)
            }
        }
    }

    private fun schedulePeriodicCheck() {
        val workerRequest =
            PeriodicWorkRequestBuilder<NetPWaitlistCodeNotificationWorker>(2, HOURS)
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()

        workManager.enqueueUniquePeriodicWork(
            TAG,
            // REPLACE because we want it to execute on every app onStart
            ExistingPeriodicWorkPolicy.KEEP,
            workerRequest,
        )
    }

    companion object {
        private const val TAG = "com.duckduckgo.netp.waitlist.checker.worker"
    }
}
