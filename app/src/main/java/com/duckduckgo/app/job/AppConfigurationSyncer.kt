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

import android.app.job.JobScheduler
import android.content.Context
import androidx.annotation.CheckResult
import com.duckduckgo.app.global.job.APP_CONFIGURATION_JOB_ID
import com.duckduckgo.app.global.job.JobBuilder
import io.reactivex.Completable
import timber.log.Timber

class AppConfigurationSyncer(
    private val jobBuilder: JobBuilder,
    private val jobScheduler: JobScheduler,
    private val appConfigurationDownloader: ConfigurationDownloader
) {

    @CheckResult
    fun scheduleImmediateSync(): Completable {
        Timber.i("Running immediate attempt to download app configuration")
        return appConfigurationDownloader.downloadTask()
    }

    /**
     * Scheduling the same job again would kill the existing job if it was running.
     *
     * So this method can be used to first query if the job is already scheduled.
     */
    fun jobScheduled(): Boolean {
        return jobScheduler.allPendingJobs
            .filter { APP_CONFIGURATION_JOB_ID == it.id }
            .count() > 0
    }

    fun scheduleRegularSync(context: Context) {
        val jobInfo = jobBuilder.appConfigurationJob(context)

        if (jobScheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS) {
            Timber.i("Job scheduled successfully")
        } else {
            Timber.e("Failed to schedule job")
        }
    }
}