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

import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import androidx.lifecycle.ProcessLifecycleOwner
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.di.AppComponent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.DaggerAppComponent
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.lifecycle.VpnProcessLifecycleObserver
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.DaggerMap
import dagger.android.AndroidInjector
import dagger.android.HasDaggerInjector
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.*
import logcat.AndroidLogcatLogger
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

private const val VPN_PROCESS_NAME = "vpn"

open class DuckDuckGoApplication : HasDaggerInjector, MultiProcessApplication() {

    @Inject
    lateinit var uncaughtExceptionHandler: Thread.UncaughtExceptionHandler

    @Inject
    lateinit var referralStateListener: AppInstallationReferrerStateListener

    @Inject
    lateinit var primaryLifecycleObserverPluginPoint: PluginPoint<MainProcessLifecycleObserver>

    @Inject
    lateinit var vpnLifecycleObserverPluginPoint: PluginPoint<VpnProcessLifecycleObserver>

    @Inject
    lateinit var activityLifecycleCallbacks: PluginPoint<com.duckduckgo.browser.api.ActivityLifecycleCallbacks>

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var injectorFactoryMap: DaggerMap<Class<*>, AndroidInjector.Factory<*, *>>

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val applicationCoroutineScope = CoroutineScope(SupervisorJob())

    open lateinit var daggerAppComponent: AppComponent

    override fun onMainProcessCreate() {
        configureLogging()
        logcat { "onMainProcessCreate $currentProcessName with pid=${android.os.Process.myPid()}" }

        configureStrictMode()
        configureDependencyInjection()
        setupActivityLifecycleCallbacks()
        configureUncaughtExceptionHandler()

        // Deprecated, we need to move all these into AppLifecycleEventObserver
        ProcessLifecycleOwner.get().lifecycle.apply {
            primaryLifecycleObserverPluginPoint.getPlugins().forEach {
                addObserver(it)
            }
        }

        appCoroutineScope.launch(dispatchers.io()) {
            referralStateListener.initialiseReferralRetrieval()
        }
    }

    override fun onSecondaryProcessCreate(shortProcessName: String) {
        if (shortProcessName != "UNKNOWN") {
            runInSecondaryProcessNamed(shortProcessName) {
                configureLogging()
                logcat { "Init for secondary process $shortProcessName with pid=${android.os.Process.myPid()}" }
                configureStrictMode()
                configureDependencyInjection()
                configureUncaughtExceptionHandler()

                if (shortProcessName == VPN_PROCESS_NAME) {
                    // ProcessLifecycleOwner doesn't know about secondary processes, so the callbacks are our own callbacks and limited to onCreate which
                    // is good enough.
                    // See https://developer.android.com/reference/android/arch/lifecycle/ProcessLifecycleOwner#get
                    ProcessLifecycleOwner.get().lifecycle.apply {
                        vpnLifecycleObserverPluginPoint.getPlugins().forEach {
                            it.onVpnProcessCreated()
                        }
                    }
                }
            }
        }
    }

    private fun setupActivityLifecycleCallbacks() {
        activityLifecycleCallbacks.getPlugins().forEach { registerActivityLifecycleCallbacks(it) }
    }

    private fun configureUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler)
        RxJavaPlugins.setErrorHandler { throwable ->
            if (throwable is UndeliverableException) {
                logcat(WARN) { "An exception happened inside RxJava code but no subscriber was still around to handle it: ${throwable.asLog()}" }
            } else {
                uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable)
            }
        }
    }

    private fun configureLogging() {
        AndroidLogcatLogger.installOnDebuggableApp(
            application = this,
            minPriority = VERBOSE,
        )
    }

    private fun configureDependencyInjection() {
        daggerAppComponent = DaggerAppComponent.builder()
            .application(this)
            .applicationCoroutineScope(applicationCoroutineScope)
            .build()
        daggerAppComponent.inject(this)
    }

    private fun configureStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .penaltyDropBox()
                    .build(),
            )
        }
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
    override fun getDir(
        name: String?,
        mode: Int,
    ): File {
        val dir = super.getDir(name, mode)
        if (!isMainProcess) {
            if (name == "webview") {
                val processName = shortProcessName
                if (processName != "UNKNOWN") {
                    return File("${dir.absolutePath}/$processName").apply {
                        logcat { ":$processName process getDir = $absolutePath" }
                        if (!exists()) {
                            mkdirs()
                        }
                    }
                }
            }
        }
        return dir
    }

    override fun getCacheDir(): File {
        val dir = super.getCacheDir()
        if (!isMainProcess) {
            val processName = shortProcessName
            if (processName != "UNKNOWN") {
                return File("${dir.absolutePath}/$processName").apply {
                    logcat { ":$processName process getCacheDir = $absolutePath" }
                    if (!exists()) {
                        mkdirs()
                    }
                }
            }
        }
        return dir
    }

    /**
     * Implementation of [HasDaggerInjector.daggerFactoryFor].
     * Similar to what dagger-android does, The [DuckDuckGoApplication] gets the [DuckDuckGoApplication.injectorFactoryMap]
     * from DI. This holds all the Dagger factories for Android types, like Activities that we create. See [BookmarksActivityComponent.Factory]
     * as an example.
     *
     * This method will return the [AndroidInjector.Factory] for the given key passed in as parameter.
     */
    override fun daggerFactoryFor(key: Class<*>): AndroidInjector.Factory<*, *> {
        return injectorFactoryMap[key]
            ?: throw RuntimeException(
                """
                Could not find the dagger component for ${key.simpleName}.
                You probably forgot to create the ${key.simpleName}Component
                """.trimIndent(),
            )
    }
}
