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
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.arch.lifecycle.ProcessLifecycleOwner
import android.os.Build
import android.support.v4.app.Fragment
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.di.AppComponent
import com.duckduckgo.app.di.DaggerAppComponent
import com.duckduckgo.app.fire.FireActivity
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.notification.NotificationRegistrar
import com.duckduckgo.app.global.shortcut.AppShortcutCreator
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.job.AppConfigurationSyncer
import com.duckduckgo.app.migration.LegacyMigration
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.APP_LAUNCH
import com.duckduckgo.app.surrogates.ResourceSurrogateLoader
import com.duckduckgo.app.trackerdetection.TrackerDataLoader
import com.squareup.leakcanary.LeakCanary
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasActivityInjector
import dagger.android.HasServiceInjector
import dagger.android.support.HasSupportFragmentInjector
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.doAsync
import timber.log.Timber
import javax.inject.Inject
import kotlin.concurrent.thread

open class DuckDuckGoApplication : HasActivityInjector, HasServiceInjector, HasSupportFragmentInjector, Application(), LifecycleObserver {

    @Inject
    lateinit var activityInjector: DispatchingAndroidInjector<Activity>

    @Inject
    lateinit var supportFragmentInjector: DispatchingAndroidInjector<Fragment>

    @Inject
    lateinit var serviceInjector: DispatchingAndroidInjector<Service>

    @Inject
    lateinit var trackerDataLoader: TrackerDataLoader

    @Inject
    lateinit var resourceSurrogateLoader: ResourceSurrogateLoader

    @Inject
    lateinit var appConfigurationSyncer: AppConfigurationSyncer

    @Inject
    lateinit var migration: LegacyMigration

    @Inject
    lateinit var statisticsUpdater: StatisticsUpdater

    @Inject
    lateinit var appInstallStore: AppInstallStore

    @Inject
    lateinit var notificationRegistrar: NotificationRegistrar

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var appShortcutCreator: AppShortcutCreator

    @Inject
    lateinit var httpsUpgrader: HttpsUpgrader

    open lateinit var daggerAppComponent: AppComponent

    override fun onCreate() {
        super.onCreate()

        if (!installLeakCanary()) return

        configureLogging()
        configureDependencyInjection()

        if (appIsRestarting()) return

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            appShortcutCreator.configureAppShortcuts(this)
        }

        initializeStatistics()
        loadTrackerData()
        configureDataDownloader()
        recordInstallationTimestamp()

        migrateLegacyDb()
        notificationRegistrar.registerApp()

        initializeHttpsUpgrader()

    }

    private fun recordInstallationTimestamp() {
        if (!appInstallStore.hasInstallTimestampRecorded()) {
            appInstallStore.installTimestamp = System.currentTimeMillis()
        }
    }

    protected open fun installLeakCanary(): Boolean {
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return false
        }
        LeakCanary.install(this)
        return true
    }

    private fun appIsRestarting(): Boolean {
        if (FireActivity.appRestarting(this)) {
            Timber.i("App restarting")
            return true
        }
        return false
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

    protected open fun configureDependencyInjection() {
        daggerAppComponent = DaggerAppComponent.builder()
            .application(this)
            .build()
        daggerAppComponent.inject(this)
    }

    private fun initializeStatistics() {
        statisticsUpdater.initializeAtb()
    }

    private fun initializeHttpsUpgrader() {
        thread { httpsUpgrader.reloadData() }
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

    override fun supportFragmentInjector(): AndroidInjector<Fragment> = supportFragmentInjector

    override fun serviceInjector(): AndroidInjector<Service> = serviceInjector


    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        pixel.fire(APP_LAUNCH)
    }

}