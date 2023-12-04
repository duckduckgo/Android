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

package com.duckduckgo.privacyprotectionspopup.impl

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupManagerImpl.Companion.TOGGLE_USAGE_REMEMBER_DURATION
import com.duckduckgo.privacyprotectionspopup.impl.db.PopupDismissDomainRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class PrivacyProtectionsPopupDomainsCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @Inject
    lateinit var popupDismissDomainRepository: PopupDismissDomainRepository

    @Inject
    lateinit var timeProvider: TimeProvider

    override suspend fun doWork(): Result {
        popupDismissDomainRepository.removeEntriesOlderThan(
            time = timeProvider.getCurrentTime() - TOGGLE_USAGE_REMEMBER_DURATION,
        )
        return Result.success()
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class PrivacyProtectionsPopupDomainsCleanupWorkerScheduler @Inject constructor(
    private val workManager: WorkManager,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        val workRequest = PeriodicWorkRequestBuilder<PrivacyProtectionsPopupDomainsCleanupWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
        )
            .addTag(WORK_REQUEST_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_REQUEST_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }

    private companion object {
        const val WORK_REQUEST_TAG = "PRIVACY_PROTECTIONS_POPUP_DOMAINS_CLEANUP_TAG"
    }
}
