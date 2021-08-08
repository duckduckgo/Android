/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.events.db

import android.content.Context
import androidx.work.*
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class FavoritesOnboardingWorkRequestBuilder @Inject constructor() {

    fun scheduleWork(): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<FavoritesOnboardingWorker>()
            .addTag(FAVORITES_ONBOARDING_WORK_TAG)
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
    }

    companion object {
        const val FAVORITES_ONBOARDING_WORK_TAG = "FavoritesOnboardingWorker"
    }
}

class FavoritesOnboardingWorker(context: Context, workerParams: WorkerParameters): CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var userEvents: UserEventsRepository

    override suspend fun doWork(): Result {
        userEvents.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE)?.let {
            //TODO: check if we need to reset timestamp
            userEvents.clearVisitedSite()
        }

        return Result.success()
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class FavoritesOnboardingWorkerInjectonPlugin @Inject constructor(
    private val userEvents: UserEventsRepository
): WorkerInjectorPlugin {
    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is FavoritesOnboardingWorker) {
            worker.userEvents = userEvents
            return true
        }
        return false
    }
}