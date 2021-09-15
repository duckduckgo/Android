/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.job

import androidx.work.WorkManager
import com.duckduckgo.app.job.JobCleaner.Companion.allDeprecatedNotificationWorkTags
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Singleton

interface JobCleaner {
    fun cleanDeprecatedJobs()

    companion object {
        private const val STICKY_SEARCH_CONTINUOUS_APP_USE_REQUEST_TAG = "com.duckduckgo.notification.schedule.continuous"
        private const val USE_OUR_APP_WORK_REQUEST_TAG = "com.duckduckgo.notification.useOurApp"
        private const val FAVORITES_ONBOARDING_WORK_TAG = "FavoritesOnboardingWorker"

        fun allDeprecatedNotificationWorkTags() = listOf(STICKY_SEARCH_CONTINUOUS_APP_USE_REQUEST_TAG, USE_OUR_APP_WORK_REQUEST_TAG)
        fun allDeprecatedWorkerTags() = listOf(FAVORITES_ONBOARDING_WORK_TAG)
    }
}

@ContributesBinding(AppObjectGraph::class)
@Singleton
class AndroidJobCleaner @Inject constructor(private val workManager: WorkManager) : JobCleaner {

    override fun cleanDeprecatedJobs() {
        allDeprecatedNotificationWorkTags().plus(allDeprecatedNotificationWorkTags()).forEach { tag ->
            workManager.cancelAllWorkByTag(tag)
        }
    }
}
