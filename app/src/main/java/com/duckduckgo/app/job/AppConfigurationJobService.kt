/*
 * Copyright (c) 2017 DuckDuckGo
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

import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ActivityScope
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

@Deprecated(
    "This is the old sync service which uses JobScheduler. " +
        "A new version, `AppConfigurationWorker` uses WorkManager and should be used going forwards."
)
@InjectWith(ActivityScope::class)
class AppConfigurationJobService : JobService() {

    @Inject
    lateinit var jobScheduler: JobScheduler

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Timber.i("Deprecated AppConfigurationJobService running. Unscheduling future syncs using this job")
        jobScheduler.cancel(LEGACY_APP_CONFIGURATION_JOB_ID)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        const val LEGACY_APP_CONFIGURATION_JOB_ID = 1
    }
}
