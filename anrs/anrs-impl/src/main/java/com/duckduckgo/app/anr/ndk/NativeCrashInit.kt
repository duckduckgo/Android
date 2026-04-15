/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.anr.ndk

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.anr.CrashPixel.APPLICATION_CRASH_NATIVE
import com.duckduckgo.app.anr.CrashPixel.APPLICATION_CRASH_NATIVE_HANDLER_REGISTERED
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.di.ProcessName
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.lifecycle.PirProcessLifecycleObserver
import com.duckduckgo.app.lifecycle.VpnProcessLifecycleObserver
import com.duckduckgo.anrs.api.CrashAnnotationContributor
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.checkMainThread
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.customtabs.api.CustomTabDetector
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.library.loader.LibraryLoader.LibraryLoaderListener
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = VpnProcessLifecycleObserver::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PirProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class NativeCrashInit @Inject constructor(
    @param:IsMainProcess private val isMainProcess: Boolean,
    private val customTabDetector: CustomTabDetector,
    private val appBuildConfig: AppBuildConfig,
    private val nativeCrashFeature: NativeCrashFeature,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val pixel: Pixel,
    @param:ProcessName private val processName: String,
    private val crashpadInitializer: CrashpadInitializer,
    private val crashAnnotationContributors: PluginPoint<CrashAnnotationContributor>,
) : MainProcessLifecycleObserver, VpnProcessLifecycleObserver, LibraryLoaderListener, PirProcessLifecycleObserver {

    private val isCustomTab: Boolean by lazy { customTabDetector.isCustomTab() }

    private val webViewVersion: String by lazy {
        if (nativeCrashFeature.nativeCrashReportsFullWebViewVersion().isEnabled()) {
            webViewVersionProvider.getFullVersion()
        } else {
            webViewVersionProvider.getMajorVersion()
        }
    }

    private val webViewPackage: String by lazy { webViewVersionProvider.getPackageName() }

    private external fun jni_register_sighandler(
        logLevel: Int,
        appVersion: String,
        processName: String,
        isCustomTab: Boolean,
        webViewPackage: String,
        webViewVersion: String,
    )

    override fun onCreate(owner: LifecycleOwner) {
        if (isMainProcess) {
            initCrashpad()
            // asyncLoadNativeLibrary()
        } else {
            logcat(ERROR) { "ndk-crash: onCreate wrongly called in a secondary process" }
        }
    }

    override fun onVpnProcessCreated() {
        if (!isMainProcess) {
            initCrashpad()
            // asyncLoadNativeLibrary()
        } else {
            logcat(ERROR) { "ndk-crash: onVpnProcessCreated wrongly called in the main process" }
        }
    }

    override fun onPirProcessCreated() {
        if (!isMainProcess) {
            initCrashpad()
            // asyncLoadNativeLibrary()
        } else {
            logcat(ERROR) { "ndk-crash: onPirProcessCreated wrongly called in the main process" }
        }
    }

    override fun success() {
        // do not call on main thread
        checkMainThread()

        runCatching {
            logcat(ERROR) { "ndk-crash: Library loaded in process $processName" }

            if (isMainProcess && !nativeCrashFeature.nativeCrashHandling().isEnabled()) return
            if (!isMainProcess && !nativeCrashFeature.nativeCrashHandlingSecondaryProcess().isEnabled()) return

            val logLevel = if (appBuildConfig.isDebug || appBuildConfig.isInternalBuild()) {
                Log.VERBOSE
            } else {
                Log.ASSERT
            }
            jni_register_sighandler(logLevel, appBuildConfig.versionName, processName, isCustomTab, webViewPackage, webViewVersion)
        }.onFailure {
            logcat(ERROR) { "ndk-crash: Error calling jni_register_sighandler: ${it.asLog()}" }
        }
    }

    override fun failure(t: Throwable) {
        logcat(ERROR) { "ndk-crash: error loading library in process $processName: ${t?.asLog()}" }
    }

    // private fun asyncLoadNativeLibrary() {
    //     LibraryLoader.loadLibrary(context, "crash-ndk", this)
    // }

    private fun initCrashpad() {
        val dynamicKeys = crashAnnotationContributors.getPlugins().flatMapTo(mutableSetOf()) { it.keys }
        check(!appBuildConfig.isInternalBuild() || dynamicKeys.size <= 60) {
            "Crashpad annotation budget exceeded: ${dynamicKeys.size} keys registered (limit ~60). " +
                "Reduce the number of keys contributed via CrashAnnotationContributor."
        }
        val initialized = crashpadInitializer.initialize(
            extraAnnotations = mapOf(
                "customTab" to "$isCustomTab",
                "webViewPackage" to webViewPackage,
                "webViewVersion" to webViewVersion,
            ),
            dynamicAnnotationKeys = dynamicKeys,
            onCrash = {
                pixel.enqueueFire(
                    APPLICATION_CRASH_NATIVE,
                    mapOf(
                        "v" to "${appBuildConfig.versionName}-${appBuildConfig.flavor}",
                        "pn" to processName,
                        "customTab" to "$isCustomTab",
                    ),
                )
            },
        )
        if (initialized) {
            pixel.fire(
                APPLICATION_CRASH_NATIVE_HANDLER_REGISTERED,
                mapOf(
                    "v" to "${appBuildConfig.versionName}-${appBuildConfig.flavor}",
                    "pn" to processName,
                    "customTab" to "$isCustomTab",
                ),
            )
        }
    }
}
