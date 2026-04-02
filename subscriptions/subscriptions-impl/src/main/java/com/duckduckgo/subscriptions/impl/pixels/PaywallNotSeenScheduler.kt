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

package com.duckduckgo.subscriptions.impl.pixels

import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.duckduckgo.anvil.annotations.ContributesMultibinding
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Schedules one-shot WorkManager jobs at each milestone to fire a "paywall not seen" pixel
 * if the user has not yet visited the paywall by that day.
 *
 * Milestones (check fires at day+1 relative to the milestone):
 *   d0  → fires after day 1
 *   d3  → fires after day 4
 *   d7  → fires after day 8
 *   d14 → fires after day 15
 *   d30 → fires after day 31
 *
 * Uses [ExistingWorkPolicy.KEEP] so re-scheduling on subsequent app starts is a no-op while
 * the work is still pending. The worker itself guards against double-firing via [PaywallMetricsDataStore].
 */
@ContributesMultibinding(scope = AppScope::class, boundType = MainProcessLifecycleObserver::class)
class PaywallNotSeenScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val paywallMetricsManager: PaywallMetricsManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        if (paywallMetricsManager.paywallEverSeen) return

        appCoroutineScope.launch(dispatcherProvider.io()) {
            scheduleMilestones()
        }
    }

    private fun scheduleMilestones() {
        MILESTONES.forEach { (dayBucket, checkAfterDays) ->
            if (paywallMetricsManager.isNotSeenDayFired(dayBucket)) return@forEach

            val request = OneTimeWorkRequestBuilder<PaywallNotSeenWorker>()
                .setInputData(workDataOf(PaywallNotSeenWorker.KEY_DAY_BUCKET to dayBucket))
                .setInitialDelay(paywallMetricsManager.delayUntilMilestone(checkAfterDays), TimeUnit.MILLISECONDS)
                .addTag(workTag(dayBucket))
                .build()

            workManager.enqueueUniqueWork(workName(dayBucket), ExistingWorkPolicy.KEEP, request)
        }
    }

    companion object {
        /** Maps day-bucket label to the number of days after install at which the check runs. */
        val MILESTONES = linkedMapOf(
            "d0" to 1L,
            "d3" to 4L,
            "d7" to 8L,
            "d14" to 15L,
            "d30" to 31L,
        )

        fun workName(dayBucket: String) = "paywall_not_seen_$dayBucket"
        fun workTag(dayBucket: String) = "paywall_not_seen_tag_$dayBucket"
    }
}
