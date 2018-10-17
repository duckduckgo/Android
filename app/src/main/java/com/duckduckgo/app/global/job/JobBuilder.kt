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

package com.duckduckgo.app.global.job

import android.app.job.JobInfo
import android.app.job.JobInfo.BACKOFF_POLICY_EXPONENTIAL
import android.content.ComponentName
import android.content.Context
import com.duckduckgo.app.job.AppConfigurationJobService
import java.util.concurrent.TimeUnit
import javax.inject.Inject

const val APP_CONFIGURATION_JOB_ID = 1

class JobBuilder @Inject constructor() {

    fun appConfigurationJob(context: Context): JobInfo {

        return JobInfo.Builder(APP_CONFIGURATION_JOB_ID, ComponentName(context, AppConfigurationJobService::class.java))
            .setPeriodic(TimeUnit.HOURS.toMillis(12))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(TimeUnit.MINUTES.toMillis(60), BACKOFF_POLICY_EXPONENTIAL)
            .setPersisted(true)
            .build()
    }
}
