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

import android.app.Application
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.shortcut.ShortcutBuilder
import com.duckduckgo.app.browser.shortcut.ShortcutReceiver
import com.duckduckgo.app.di.AppComponent
import com.duckduckgo.app.di.DaggerAppComponent
import com.duckduckgo.app.fire.FireActivity
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.global.Theming.initializeTheme
import com.duckduckgo.app.global.initialization.AppDataLoader
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.global.shortcut.AppShortcutCreator
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.job.AppConfigurationSyncer
import com.duckduckgo.app.job.WorkScheduler
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.AtbInitializer
import com.duckduckgo.app.statistics.api.OfflinePixelScheduler
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.pixels.AppPixelName.APP_LAUNCH
import com.duckduckgo.app.surrogates.ResourceSurrogateLoader
import com.duckduckgo.app.trackerdetection.TrackerDataLoader
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.anko.doAsync
import timber.log.Timber
import javax.inject.Inject
import kotlin.concurrent.thread

open class DuckDuckGoApplication : HasAndroidInjector, Application(), LifecycleObserver {

    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var trackerDataLoader: TrackerDataLoader

    @Inject
    lateinit var resourceSurrogateLoader: ResourceSurrogateLoader

    @Inject
    lateinit var appConfigurationSyncer: AppConfigurationSyncer

    @Inject
    lateinit var appInstallStore: AppInstallStore

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var notificationRegistrar: NotificationRegistrar

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var appShortcutCreator: AppShortcutCreator

    @Inject
    lateinit var httpsUpgrader: HttpsUpgrader

    @Inject
    lateinit var unsentForgetAllPixelStore: UnsentForgetAllPixelStore

    @Inject
    lateinit var offlinePixelScheduler: OfflinePixelScheduler

    @Inject
    lateinit var workScheduler: WorkScheduler

    @Inject
    lateinit var appDataLoader: AppDataLoader

    @Inject
    lateinit var alertingUncaughtExceptionHandler: AlertingUncaughtExceptionHandler

    @Inject
    lateinit var referralStateListener: AppInstallationReferrerStateListener

    @Inject
    lateinit var atbInitializer: AtbInitializer

    @Inject
    lateinit var shortcutReceiver: ShortcutReceiver

    @Inject
    lateinit var lifecycleObserverPluginPoint: PluginPoint<LifecycleObserver>

    @Inject
    lateinit var webViewHttpAuthStore: WebViewHttpAuthStore

    private var launchedByFireAction: Boolean = false

    private val applicationCoroutineScope = CoroutineScope(SupervisorJob())

    open lateinit var daggerAppComponent: AppComponent

    override fun onCreate() {
        super.onCreate()

        configureLogging()
        Timber.i("Application Started")
        if (appIsRestarting()) return

        Timber.i("Creating DuckDuckGoApplication")
        configureDependencyInjection()
        configureUncaughtExceptionHandler()

        ProcessLifecycleOwner.get().lifecycle.apply {
            addObserver(this@DuckDuckGoApplication)
            lifecycleObserverPluginPoint.getPlugins().forEach {
                addObserver(it)
                Need to add this here
                it.addObserver(webViewHttpAuthStore)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            appShortcutCreator.configureAppShortcuts(this)
        }

        recordInstallationTimestamp()
        initializeTheme(settingsDataStore)
        loadTrackerData()
        configureDataDownloader()
        scheduleOfflinePixels()

        notificationRegistrar.registerApp()
        registerReceiver(shortcutReceiver, IntentFilter(ShortcutBuilder.USE_OUR_APP_SHORTCUT_ADDED_ACTION))

        initializeHttpsUpgrader()
        submitUnsentFirePixels()

        GlobalScope.launch {
            referralStateListener.initialiseReferralRetrieval()
            appDataLoader.loadData()
        }
    }

    private fun configureUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(alertingUncaughtExceptionHandler)
        RxJavaPlugins.setErrorHandler { throwable ->
            if (throwable is UndeliverableException) {
                Timber.w(throwable, "An exception happened inside RxJava code but no subscriber was still around to handle it")
            } else {
                alertingUncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable)
            }
        }
    }

    private fun recordInstallationTimestamp() {
        if (!appInstallStore.hasInstallTimestampRecorded()) {
            appInstallStore.installTimestamp = System.currentTimeMillis()
        }
    }

    private fun appIsRestarting(): Boolean {
        if (FireActivity.appRestarting(this)) {
            Timber.i("App restarting")
            return true
        }
        return false
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
            .applicationCoroutineScope(applicationCoroutineScope)
            .build()
        daggerAppComponent.inject(this)
    }

    private fun initializeHttpsUpgrader() {
        thread { httpsUpgrader.reloadData() }
    }

    private fun submitUnsentFirePixels() {
        val count = unsentForgetAllPixelStore.pendingPixelCountClearData
        Timber.i("Found $count unsent clear data pixels")
        if (count > 0) {
            val timeDifferenceMillis = System.currentTimeMillis() - unsentForgetAllPixelStore.lastClearTimestamp
            if (timeDifferenceMillis <= APP_RESTART_CAUSED_BY_FIRE_GRACE_PERIOD) {
                Timber.i("The app was re-launched as a result of the fire action being triggered (happened ${timeDifferenceMillis}ms ago)")
                launchedByFireAction = true
            }
            for (i in 1..count) {
                pixel.fire(AppPixelName.FORGET_ALL_EXECUTED)
            }
            unsentForgetAllPixelStore.resetCount()
        }
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
            .doAfterTerminate {
                appConfigurationSyncer.scheduleRegularSync()
            }
            .subscribe({}, { Timber.w("Failed to download initial app configuration ${it.localizedMessage}") })
    }

    private fun scheduleOfflinePixels() {
        offlinePixelScheduler.scheduleOfflinePixels()
    }

    override fun androidInjector(): AndroidInjector<Any> {
        return androidInjector
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        if (launchedByFireAction) {
            launchedByFireAction = false
            Timber.i("Suppressing app launch pixel")
            return
        }
        pixel.fire(APP_LAUNCH)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onAppResumed() {
        notificationRegistrar.updateStatus()
        GlobalScope.launch {
            workScheduler.scheduleWork()
            atbInitializer.initialize()
        }
    }

    companion object {
        private const val APP_RESTART_CAUSED_BY_FIRE_GRACE_PERIOD: Long = 10_000L
    }

}
