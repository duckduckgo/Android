/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.di

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobWorkItem
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(
    scope = AppScope::class,
    replaces = [JobsModule::class]
)
class StubJobSchedulerModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun providesJobScheduler(): JobScheduler {
        return object : JobScheduler() {
            override fun enqueue(
                job: JobInfo,
                work: JobWorkItem
            ): Int = JobScheduler.RESULT_SUCCESS

            override fun schedule(job: JobInfo): Int = JobScheduler.RESULT_SUCCESS

            override fun cancel(jobId: Int) {}

            override fun cancelAll() {}

            override fun getAllPendingJobs(): MutableList<JobInfo> = mutableListOf()

            override fun getPendingJob(jobId: Int): JobInfo? = null
        }
    }
}
