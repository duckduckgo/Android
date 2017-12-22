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

package com.duckduckgo.app.global

import android.app.Activity
import android.app.Application
import android.app.Service
import android.app.job.JobScheduler
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.di.DaggerAppComponent
import com.duckduckgo.app.global.job.JobBuilder
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasActivityInjector
import dagger.android.HasServiceInjector
import timber.log.Timber
import javax.inject.Inject

class DuckDuckGoApplication : HasActivityInjector, HasServiceInjector, Application() {

    @Inject
    lateinit var activityInjector: DispatchingAndroidInjector<Activity>

    @Inject
    lateinit var serviceInjector: DispatchingAndroidInjector<Service>

    @Inject
    lateinit var crashReportingInitializer: CrashReportingInitializer

    @Inject
    lateinit var jobBuilder: JobBuilder

    @Inject
    lateinit var jobScheduler: JobScheduler

    override fun onCreate() {
        super.onCreate()

        configureDependencyInjection()
        configureLogging()
        configureCrashReporting()
        configureDataDownloader()
    }

    private fun configureLogging() {
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }

    private fun configureDependencyInjection() {
        DaggerAppComponent.builder()
                .application(this)
                .create(this)
                .inject(this)
    }

    private fun configureCrashReporting() {
        crashReportingInitializer.init(this)
    }

    private fun configureDataDownloader() {
        val jobInfo = jobBuilder.appConfigurationJob(this)

        val schedulingRequired = jobScheduler.allPendingJobs
                .filter { jobInfo.id == it.id }
                .count() == 0

        if(schedulingRequired) {
            Timber.i("Scheduling of background sync job, successful = %s", jobScheduler.schedule(jobInfo))
        } else {
            Timber.i("Job already scheduled; no need to schedule again")
        }
    }

    override fun activityInjector(): AndroidInjector<Activity> = activityInjector

    override fun serviceInjector(): AndroidInjector<Service> = serviceInjector
}
