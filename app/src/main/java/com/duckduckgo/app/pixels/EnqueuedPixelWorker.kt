/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.pixels

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.*
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.DEFAULT_BROWSER
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.IS_DUCKDUCKGO_PACKAGE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.WEBVIEW_FULL_VERSION
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.WEBVIEW_VERSION
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.customtabs.api.CustomTabDetector
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupExperimentExternalPixels
import com.duckduckgo.verifiedinstallation.IsVerifiedPlayStoreInstall
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class EnqueuedPixelWorker @Inject constructor(
    private val workManager: WorkManager,
    private val pixel: Provider<Pixel>,
    private val unsentForgetAllPixelStore: UnsentForgetAllPixelStore,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val customTabDetector: CustomTabDetector,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val privacyProtectionsPopupExperimentExternalPixels: PrivacyProtectionsPopupExperimentExternalPixels,
    private val isVerifiedPlayStoreInstall: IsVerifiedPlayStoreInstall,
    private val appBuildConfig: AppBuildConfig,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    private var launchedByFireAction: Boolean = false

    override fun onCreate(owner: LifecycleOwner) {
        scheduleWorker(workManager)
        launchedByFireAction = isLaunchByFireAction()
    }

    override fun onStart(owner: LifecycleOwner) {
        logcat { "onStart called" }

        if (launchedByFireAction) {
            // skip the next on_start if branch
            logcat(INFO) { "Suppressing app launch pixel" }
            launchedByFireAction = false
            return
        }
        logcat(INFO) { "Sending app launch pixel" }
        val collectWebViewFullVersion =
            androidBrowserConfigFeature.self().isEnabled() && androidBrowserConfigFeature.collectFullWebViewVersion().isEnabled()
        val paramsMap = mutableMapOf<String, String>().apply {
            put(WEBVIEW_VERSION, webViewVersionProvider.getMajorVersion())
            put(DEFAULT_BROWSER, defaultBrowserDetector.isDefaultBrowser().toString())
            put(IS_DUCKDUCKGO_PACKAGE, isDuckDuckGoAppPackage(appBuildConfig.applicationId))
            if (collectWebViewFullVersion) {
                put(WEBVIEW_FULL_VERSION, webViewVersionProvider.getFullVersion())
            }
        }.toMap()
        appCoroutineScope.launch {
            val popupExperimentParams = privacyProtectionsPopupExperimentExternalPixels.getPixelParams()
            val parameters = paramsMap + popupExperimentParams
            pixel.get().fire(
                pixel = AppPixelName.APP_LAUNCH,
                parameters = parameters,
            )

            if (isVerifiedPlayStoreInstall() && !customTabDetector.isCustomTab()) {
                pixel.get().fire(
                    pixel = AppPixelName.APP_LAUNCH_VERIFIED_INSTALL,
                    parameters = parameters,
                )
            }
        }
    }

    private fun isDuckDuckGoAppPackage(applicationId: String): String {
        return (applicationId == "com.duckduckgo.mobile.android" || applicationId == "com.duckduckgo.mobile.android.debug").toString()
    }

    private fun isLaunchByFireAction(): Boolean {
        val timeDifferenceMillis = System.currentTimeMillis() - unsentForgetAllPixelStore.lastClearTimestamp
        if (timeDifferenceMillis <= APP_RESTART_CAUSED_BY_FIRE_GRACE_PERIOD) {
            logcat(INFO) { "The app was re-launched as a result of the fire action being triggered (happened ${timeDifferenceMillis}ms ago)" }
            return true
        }
        return false
    }

    fun submitUnsentFirePixels() {
        val count = unsentForgetAllPixelStore.pendingPixelCountClearData
        logcat(INFO) { "Found $count unsent clear data pixels" }
        if (count > 0) {
            for (i in 1..count) {
                pixel.get().fire(AppPixelName.FORGET_ALL_EXECUTED)
            }
            unsentForgetAllPixelStore.resetCount()
        }
    }

    companion object {
        private const val APP_RESTART_CAUSED_BY_FIRE_GRACE_PERIOD: Long = 10_000L
        private const val WORKER_SEND_ENQUEUED_PIXELS = "com.duckduckgo.pixels.enqueued.worker"

        private fun scheduleWorker(workManager: WorkManager) {
            logcat(VERBOSE) { "Scheduling the EnqueuedPixelWorker" }

            val request = PeriodicWorkRequestBuilder<RealEnqueuedPixelWorker>(2, TimeUnit.HOURS)
                .addTag(WORKER_SEND_ENQUEUED_PIXELS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(WORKER_SEND_ENQUEUED_PIXELS, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

@ContributesWorker(AppScope::class)
class RealEnqueuedPixelWorker(
    val context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var enqueuedPixelWorker: EnqueuedPixelWorker

    override suspend fun doWork(): Result {
        logcat(VERBOSE) { "Sending enqueued pixels" }

        enqueuedPixelWorker.submitUnsentFirePixels()

        return Result.success()
    }
}
