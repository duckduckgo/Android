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

package com.duckduckgo.app.di

import android.app.job.JobScheduler
import android.content.Context
import androidx.work.WorkManager
import com.duckduckgo.app.job.AndroidJobCleaner
import com.duckduckgo.app.job.AndroidWorkScheduler
import com.duckduckgo.app.job.JobCleaner
import com.duckduckgo.app.job.WorkScheduler
import com.duckduckgo.app.notification.AndroidNotificationScheduler
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class JobsModule {

    @Singleton
    @Provides
    fun providesJobScheduler(context: Context): JobScheduler {
        return context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    }

    @Singleton
    @Provides
    fun providesJobCleaner(workManager: WorkManager): JobCleaner {
        return AndroidJobCleaner(workManager)
    }

    @Singleton
    @Provides
    fun providesWorkScheduler(notificationScheduler: AndroidNotificationScheduler, jobCleaner: JobCleaner): WorkScheduler {
        return AndroidWorkScheduler(notificationScheduler, jobCleaner)
    }
}
