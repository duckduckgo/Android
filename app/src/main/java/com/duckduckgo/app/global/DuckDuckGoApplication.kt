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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.di.AppComponent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.DaggerAppComponent
import com.duckduckgo.app.di.component.BookmarksActivityComponent
import com.duckduckgo.app.fire.FireActivity
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.APP_LAUNCH
import com.duckduckgo.app.process.ProcessDetector
import com.duckduckgo.app.process.ProcessDetector.DuckDuckGoProcess
import com.duckduckgo.app.process.ProcessDetector.DuckDuckGoProcess.VpnProcess
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.mobile.android.vpn.service.VpnUncaughtExceptionHandler
import com.jakewharton.threetenabp.AndroidThreeTen
import dagger.android.AndroidInjector
import dagger.android.HasDaggerInjector
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import kotlinx.coroutines.*
import org.threeten.bp.zone.ZoneRulesProvider
import timber.log.Timber
import java.io.File
import javax.inject.Inject

open class DuckDuckGoApplication : HasDaggerInjector, Application(), LifecycleObserver {

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var unsentForgetAllPixelStore: UnsentForgetAllPixelStore

    @Inject
    lateinit var alertingUncaughtExceptionHandler: AlertingUncaughtExceptionHandler

    @Inject
    lateinit var vpnUncaughtExceptionHandler: VpnUncaughtExceptionHandler

    @Inject
    lateinit var referralStateListener: AppInstallationReferrerStateListener

    @Inject
    lateinit var lifecycleObserverPluginPoint: PluginPoint<LifecycleObserver>

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var injectorFactoryMap: Map<@JvmSuppressWildcards Class<*>, @JvmSuppressWildcards AndroidInjector.Factory<*>>

    private val processDetector = ProcessDetector()

    private var launchedByFireAction: Boolean = false

    private val applicationCoroutineScope = CoroutineScope(SupervisorJob())

    open lateinit var daggerAppComponent: AppComponent

    override fun onCreate() {
        super.onCreate()

        configureLogging()

        val currentProcess = processDetector.detectProcess(this)
        Timber.i("Creating DuckDuckGoApplication. Process %s", currentProcess)

        if (appIsRestarting()) return

        configureDependencyInjection()
        configureUncaughtExceptionHandler(currentProcess)
        initializeDateLibrary()

        if (appIsRestarting()) return
        if (currentProcess is VpnProcess) {
            Timber.i("VPN process, no further logic executed in application onCreate()")
            return
        }

        ProcessLifecycleOwner.get().lifecycle.apply {
            addObserver(this@DuckDuckGoApplication)
            lifecycleObserverPluginPoint.getPlugins().forEach {
                Timber.d("Registering application lifecycle observer: ${it.javaClass.canonicalName}")
                addObserver(it)
            }
        }

        submitUnsentFirePixels()

        appCoroutineScope.launch {
            referralStateListener.initialiseReferralRetrieval()
        }
    }

    private fun configureUncaughtExceptionHandler(currentProcess: DuckDuckGoProcess) {
        when (currentProcess) {
            is VpnProcess -> configureUncaughtExceptionHandlerVpn()
            is DuckDuckGoProcess.BrowserProcess -> configureUncaughtExceptionHandlerBrowser()
            else -> Timber.w("Unknown process: Not configuring uncaught exception handler for %s", currentProcess)

        }
    }

    private fun configureUncaughtExceptionHandlerBrowser() {
        Thread.setDefaultUncaughtExceptionHandler(alertingUncaughtExceptionHandler)
        RxJavaPlugins.setErrorHandler { throwable ->
            if (throwable is UndeliverableException) {
                Timber.w(throwable, "An exception happened inside RxJava code but no subscriber was still around to handle it")
            } else {
                alertingUncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable)
            }
        }
    }

    private fun configureUncaughtExceptionHandlerVpn() {
        Thread.setDefaultUncaughtExceptionHandler(vpnUncaughtExceptionHandler)
    }

    private fun appIsRestarting(): Boolean {
        if (FireActivity.appRestarting(this)) {
            Timber.i("App restarting")
            return true
        }
        return false
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
        if (name == "webview" && processDetector.detectProcess(this) is VpnProcess) {
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
        if (processDetector.detectProcess(this) is VpnProcess) {
            return File("${dir.absolutePath}/vpn").apply {
                Timber.d(":vpn process getCacheDir = $absolutePath")
                if (!exists()) {
                    mkdirs()
                }
            }
        }
        return dir
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

    private fun initializeDateLibrary() {
        AndroidThreeTen.init(this)
        // Query the ZoneRulesProvider so that it is loaded on a background coroutine
        GlobalScope.launch(Dispatchers.IO) {
            ZoneRulesProvider.getAvailableZoneIds()
        }
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

    companion object {
        private const val APP_RESTART_CAUSED_BY_FIRE_GRACE_PERIOD: Long = 10_000L
    }

    /**
     * Implementation of [HasDaggerInjector.daggerFactoryFor].
     * Similar to what dagger-android does, The [DuckDuckGoApplication] gets the [DuckDuckGoApplication.injectorFactoryMap]
     * from DI. This holds all the Dagger factories for Android types, like Activities that we create. See [BookmarksActivityComponent.Factory]
     * as an example.
     *
     * This method will return the [AndroidInjector.Factory] for the given key passed in as parameter.
     */
    override fun daggerFactoryFor(key: Any): AndroidInjector.Factory<*> {
        return injectorFactoryMap[key]
            ?: throw RuntimeException(
                """
                Could not find the dagger component for ${key::class.simpleName}.
                You probably forgot to create the ${key::class.simpleName}Component
                """.trimIndent()
            )
    }
}
