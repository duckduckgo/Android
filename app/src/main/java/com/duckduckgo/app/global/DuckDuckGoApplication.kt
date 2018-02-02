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
import com.duckduckgo.app.surrogates.ResourceSurrogateLoader
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.di.DaggerAppComponent
import com.duckduckgo.app.job.AppConfigurationSyncer
import com.duckduckgo.app.migration.LegacyMigration
import com.duckduckgo.app.trackerdetection.TrackerDataLoader
import com.squareup.leakcanary.LeakCanary
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasActivityInjector
import dagger.android.HasServiceInjector
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.doAsync
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
    lateinit var trackerDataLoader: TrackerDataLoader

    @Inject
    lateinit var resourceSurrogateLoader: ResourceSurrogateLoader

    @Inject
    lateinit var appConfigurationSyncer: AppConfigurationSyncer

    @Inject
    lateinit var migration: LegacyMigration

    override fun onCreate() {
        super.onCreate()

        if (!installLeakCanary()) return

        configureDependencyInjection()
        configureLogging()
        configureCrashReporting()

        loadTrackerData()
        configureDataDownloader()

        migrateLegacyDb()
    }

    private fun installLeakCanary(): Boolean {
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return false;
        }
        LeakCanary.install(this);
        return true
    }

    private fun migrateLegacyDb() {
        doAsync {
            migration.start { favourites, searches ->
                Timber.d("Migrated $favourites favourites, $searches")
            }
        }
    }

    private fun loadTrackerData() {
        doAsync {
            trackerDataLoader.loadData()
            resourceSurrogateLoader.loadData()
        }
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

    /**
     * Immediately syncs data. Upon completion (successful or error),
     * it will schedule a recurring job to keep the data in sync.
     *
     * We only process data if it has changed so these calls are inexpensive.
     */
    private fun configureDataDownloader() {
        appConfigurationSyncer.scheduleImmediateSync()
                .subscribeOn(Schedulers.io())
                .doAfterTerminate({
                    appConfigurationSyncer.scheduleRegularSync(this)
                })
                .subscribe({}, { Timber.w("Failed to download initial app configuration ${it.localizedMessage}") })
    }

    override fun activityInjector(): AndroidInjector<Activity> = activityInjector

    override fun serviceInjector(): AndroidInjector<Service> = serviceInjector
}
