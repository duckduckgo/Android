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

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserObserver
import com.duckduckgo.app.browser.shortcut.ShortcutBuilder
import com.duckduckgo.app.browser.shortcut.ShortcutReceiver
import com.duckduckgo.app.di.AppComponent
import com.duckduckgo.app.di.DaggerAppComponent
import com.duckduckgo.app.fire.*
import com.duckduckgo.app.global.Theming.initializeTheme
import com.duckduckgo.app.global.initialization.AppDataLoader
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.rating.AppEnjoymentLifecycleObserver
import com.duckduckgo.app.global.shortcut.AppShortcutCreator
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.job.AppConfigurationSyncer
import com.duckduckgo.app.job.VpnStatsReportingScheduler
import com.duckduckgo.app.job.WorkScheduler
import com.duckduckgo.app.notification.NotificationRegistrar
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.AtbInitializer
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.api.OfflinePixelScheduler
import com.duckduckgo.app.statistics.api.OfflinePixelSender
import com.duckduckgo.app.statistics.api.PixelSender
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.APP_LAUNCH
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.surrogates.ResourceSurrogateLoader
import com.duckduckgo.app.tabs.db.TabsDbSanitizer
import com.duckduckgo.app.trackerdetection.TrackerDataLoader
import com.duckduckgo.app.usage.app.AppDaysUsedRecorder
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.jakewharton.threetenabp.AndroidThreeTen
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.*
import org.jetbrains.anko.doAsync
import org.threeten.bp.zone.ZoneRulesProvider
import timber.log.Timber
import java.io.File
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
    lateinit var defaultBrowserObserver: DefaultBrowserObserver

    @Inject
    lateinit var statisticsUpdater: StatisticsUpdater

    @Inject
    lateinit var statisticsDataStore: StatisticsDataStore

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
    lateinit var dataClearerForegroundAppRestartPixel: DataClearerForegroundAppRestartPixel

    @Inject
    lateinit var offlinePixelScheduler: OfflinePixelScheduler

    @Inject
    lateinit var offlinePixelCountDataStore: OfflinePixelCountDataStore

    @Inject
    lateinit var dataClearer: DataClearer

    @Inject
    lateinit var workScheduler: WorkScheduler

    @Inject
    lateinit var appEnjoymentLifecycleObserver: AppEnjoymentLifecycleObserver

    @Inject
    lateinit var appDaysUsedRecorder: AppDaysUsedRecorder

    @Inject
    lateinit var appDataLoader: AppDataLoader

    @Inject
    lateinit var offlinePixelSender: OfflinePixelSender

    @Inject
    lateinit var pixelSender: PixelSender

    @Inject
    lateinit var alertingUncaughtExceptionHandler: AlertingUncaughtExceptionHandler

    @Inject
    lateinit var referralStateListener: AppInstallationReferrerStateListener

    @Inject
    lateinit var atbInitializer: AtbInitializer

    @Inject
    lateinit var shortcutReceiver: ShortcutReceiver

    @Inject
    lateinit var variantManager: VariantManager

    @Inject
    lateinit var userStageStore: UserStageStore

    @Inject
    lateinit var fireFireAnimationLoader: FireAnimationLoader

    @Inject
    lateinit var tabsDbSanitizer: TabsDbSanitizer

    @Inject
    lateinit var vpnStatsReporting: VpnStatsReportingScheduler

    private var launchedByFireAction: Boolean = false

    private val applicationCoroutineScope = CoroutineScope(SupervisorJob())

    open lateinit var daggerAppComponent: AppComponent

    override fun onCreate() {
        super.onCreate()

        configureLogging()
        configureDependencyInjection()
        configureUncaughtExceptionHandler()
        initializeDateLibrary()

        val processName = processName()
        Timber.i("Creating DuckDuckGo Application. Process name: $processName")

        // vtodo Temporary inclusion of Firebase while in internal testing
        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        FirebasePerformance.getInstance().isPerformanceCollectionEnabled = !BuildConfig.DEBUG

        if (appIsRestarting()) return
        if (processName.isVpnProcess()) {
            Timber.i("VPN process, no further logic executed in application onCreate()")
            return
        }

        ProcessLifecycleOwner.get().lifecycle.also {
            it.addObserver(this)
            it.addObserver(dataClearer)
            it.addObserver(appDaysUsedRecorder)
            it.addObserver(defaultBrowserObserver)
            it.addObserver(appEnjoymentLifecycleObserver)
            it.addObserver(dataClearerForegroundAppRestartPixel)
            it.addObserver(userStageStore)
            it.addObserver(pixelSender)
            it.addObserver(fireFireAnimationLoader)
            it.addObserver(tabsDbSanitizer)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            appShortcutCreator.configureAppShortcuts(this)
        }

        recordInstallationTimestamp()
        initializeTheme(settingsDataStore)
        loadTrackerData()
        configureDataDownloader()
        scheduleOfflinePixels()
        scheduleVpnStatsReporting()

        notificationRegistrar.registerApp()
        registerReceiver(shortcutReceiver, IntentFilter(ShortcutBuilder.USE_OUR_APP_SHORTCUT_ADDED_ACTION))

        initializeHttpsUpgrader()
        submitUnsentFirePixels()

        GlobalScope.launch {
            referralStateListener.initialiseReferralRetrieval()
            appDataLoader.loadData()
        }
    }

    private fun String.isVpnProcess(): Boolean {
        return this.endsWith(":vpn")
    }

    // vtodo maybe remove at some point
    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    private fun processName(): String {
        if (Build.VERSION.SDK_INT >= 28) {
            return getProcessName()
        }

        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.runningAppProcesses.firstOrNull { it.pid == Process.myPid() }?.processName.orEmpty()
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

    // vtodo - Work around for https://crbug.com/558377
    // AndroidInjection.inject(this) creates a new instance of the DuckDuckGoApplication (because we are in a new process)
    // This has several disadvantages:
    //   1. our app is of massive size, because we are duplicating our Dagger graph
    //   2. we are hitting this bug in https://crbug.com/558377, because some of the injected dependencies may eventually
    //      depend in something webview-related
    //
    // We need to override getDir and getCacheDir so that the webview does not share the same data dir across processes
    // This is hacky hacky but should be OK for now as we don't use the webview in the VPN, it is just an issue with
    // injecting/creating dependencies
    //
    // A proper fix should be to create a VpnServiceComponent that just provide the dependencies needed by the VPN, which would
    // also help with memory
    override fun getDir(name: String?, mode: Int): File {
        val dir = super.getDir(name, mode)
        if (name == "webview" && processName().isVpnProcess()) {
            return File("${dir.absolutePath}/vpn").apply {
                Timber.d(":vpn process getDir = $absolutePath")
                if (!exists()) {
                    mkdirs()
                }
            }
        }
        return dir
    }

    override fun getCacheDir(): File {
        val dir = super.getCacheDir()
        if (processName().isVpnProcess()) {
            return File("${dir.absolutePath}/vpn").apply {
                Timber.d(":vpn process getCacheDir = $absolutePath")
                if (!exists()) {
                    mkdirs()
                }
            }
        }
        return dir
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
                pixel.fire(Pixel.PixelName.FORGET_ALL_EXECUTED)
            }
            unsentForgetAllPixelStore.resetCount()
        }

        dataClearerForegroundAppRestartPixel.firePendingPixels()
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

    private fun scheduleVpnStatsReporting() {
        vpnStatsReporting.schedule()
    }

    private fun initializeDateLibrary() {
        AndroidThreeTen.init(this)
        // Query the ZoneRulesProvider so that it is loaded on a background coroutine
        GlobalScope.launch(Dispatchers.IO) {
            ZoneRulesProvider.getAvailableZoneIds()
        }
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
