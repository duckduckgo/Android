/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.annotation.SuppressLint
import android.app.ApplicationExitInfo
import androidx.annotation.MainThread
import com.duckduckgo.app.browser.WebViewPixelName.WEB_RENDERER_GONE_CRASH
import com.duckduckgo.app.browser.WebViewPixelName.WEB_RENDERER_GONE_KILLED
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.startup.metrics.MemoryCollector
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface RenderProcessGoneReporter {
    @MainThread
    fun onRenderProcessGone(didCrash: Boolean)
}

/**
 * WebViews in the same profile share a renderer process, and when it dies Android calls onRenderProcessGone on each of
 * them, one after another within a single main-thread message. Firing per callback counted one death once per live tab,
 * so this collects the callbacks delivered before the next main-thread message and fires a single pixel for them.
 * Disabling [RenderProcessGonePixelFeature] restores the parameterless pixel per callback.
 */
@ContributesIntoSet(AppScope::class, binding = binding<BrowserLifecycleObserver>())
@ContributesBinding(AppScope::class, binding = binding<RenderProcessGoneReporter>())
@SingleIn(AppScope::class)
class RealRenderProcessGoneReporter @Inject constructor(
    private val pixel: Pixel,
    private val memoryCollector: MemoryCollector,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val renderProcessGonePixelFeature: RenderProcessGonePixelFeature,
    private val rendererExitInfoProvider: RendererExitInfoProvider,
    private val currentTimeProvider: CurrentTimeProvider,
) : RenderProcessGoneReporter, BrowserLifecycleObserver {

    // Open/close transitions as (wall-clock time, isOpen), so the app state can be placed at the death time, which the
    // system records and which can be long before the callback when the app was frozen.
    private val transitions = ArrayDeque<Pair<Long, Boolean>>()
    private var pendingDeath: RendererDeath? = null

    // One lookup at a time, in callback order, so concurrent deaths don't race for the same exit record.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val reportingDispatcher = dispatchers.io().limitedParallelism(1)

    override fun onOpen(isFreshLaunch: Boolean) = recordTransition(isOpen = true)

    override fun onClose() = recordTransition(isOpen = false)

    private fun recordTransition(isOpen: Boolean) = synchronized(transitions) {
        transitions.addLast(currentTimeProvider.currentTimeMillis() to isOpen)
        if (transitions.size > MAX_TRANSITIONS) transitions.removeFirst()
    }

    override fun onRenderProcessGone(didCrash: Boolean) {
        pendingDeath?.let {
            it.affectedWebViews++
            return
        }
        // Checked only when a death starts, so a remote flip mid-death can't split one death across both reporting modes.
        if (!renderProcessGonePixelFeature.self().isEnabled()) {
            pixel.fire(pixelName(didCrash))
            return
        }
        pendingDeath = RendererDeath(didCrash = didCrash, callbackTimeMillis = currentTimeProvider.currentTimeMillis())
        // Dispatchers.Main posts rather than running inline, so this runs once the current message has notified every WebView.
        appCoroutineScope.launch(dispatchers.main()) {
            val death = pendingDeath ?: return@launch
            pendingDeath = null
            withContext(reportingDispatcher) { fire(death) }
        }
    }

    private fun fire(death: RendererDeath) {
        val lookup = if (rendererExitInfoProvider.isSupported) rendererExitInfoProvider.findRendererExit(death.callbackTimeMillis) else null
        val exit = (lookup as? RendererExitLookup.Found)?.exit
        val deathTimeMillis = exit?.timestampMillis ?: death.callbackTimeMillis
        val timeInBackground = timeInBackgroundAt(deathTimeMillis)
        val params = buildMap {
            put(PARAM_APP_STATE, if (timeInBackground == null) "foreground" else "background")
            put(PARAM_AFFECTED_WEBVIEWS, bucketWebViewCount(death.affectedWebViews))
            timeInBackground?.let { put(PARAM_TIME_IN_BACKGROUND, it) }
            memoryCollector.collectDeviceRamBucket()?.let { put(PARAM_DEVICE_RAM, it) }
            when (lookup) {
                is RendererExitLookup.Found -> {
                    put(PARAM_EXIT_REASON, exitReason(lookup.exit.reason))
                    put(PARAM_RENDERER_PSS, bucketPss(lookup.exit.pssKb))
                }
                RendererExitLookup.Ambiguous -> put(PARAM_EXIT_REASON, "ambiguous")
                RendererExitLookup.NotFound -> put(PARAM_EXIT_REASON, "not_found")
                null -> Unit
            }
        }
        // Stored before sending, because memory sweeps often kill the app right after its renderer, and a direct send is lost
        // when the process dies or the device is offline before the request completes.
        pixel.enqueueFire(pixelName(death.didCrash), params)
    }

    /** Null when the app was open at [timeMillis], otherwise how long it had been closed. */
    private fun timeInBackgroundAt(timeMillis: Long): String? = synchronized(transitions) {
        val last = transitions.lastOrNull { (at, _) -> at <= timeMillis } ?: return "never_opened"
        val (at, isOpen) = last
        if (isOpen) null else bucketTimeInBackground(timeMillis - at)
    }

    private fun pixelName(didCrash: Boolean) = if (didCrash) WEB_RENDERER_GONE_CRASH else WEB_RENDERER_GONE_KILLED

    private class RendererDeath(
        val didCrash: Boolean,
        val callbackTimeMillis: Long,
        var affectedWebViews: Int = 1,
    )

    companion object {
        const val PARAM_APP_STATE = "app_state"
        const val PARAM_AFFECTED_WEBVIEWS = "affected_webviews"
        const val PARAM_DEVICE_RAM = "device_ram_bucket"
        const val PARAM_TIME_IN_BACKGROUND = "time_in_background"
        const val PARAM_EXIT_REASON = "exit_reason"
        const val PARAM_RENDERER_PSS = "renderer_pss"
        private const val MAX_TRANSITIONS = 32

        private fun bucketWebViewCount(count: Int): String = when {
            count <= 1 -> "1"
            count <= 3 -> "2-3"
            count <= 5 -> "4-5"
            count <= 10 -> "6-10"
            else -> "11+"
        }

        private fun bucketTimeInBackground(millis: Long): String = when {
            millis < 10_000 -> "<10s"
            millis < 60_000 -> "10s-1m"
            millis < 600_000 -> "1m-10m"
            millis < 3_600_000 -> "10m-1h"
            else -> "1h+"
        }

        // The constants are compile-time ints, so referencing ones newer than minSdk is safe.
        @SuppressLint("InlinedApi")
        private fun exitReason(reason: Int): String = when (reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
            ApplicationExitInfo.REASON_SIGNALED -> "signaled"
            ApplicationExitInfo.REASON_CRASH -> "crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource_usage"
            ApplicationExitInfo.REASON_FREEZER -> "freezer"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
            ApplicationExitInfo.REASON_OTHER -> "other_system_kill"
            ApplicationExitInfo.REASON_UNKNOWN -> "unknown"
            else -> "unmapped"
        }

        private fun bucketPss(pssKb: Long): String = when {
            pssKb <= 0 -> "unknown"
            pssKb < 100 * 1024 -> "<100MB"
            pssKb < 200 * 1024 -> "100-200MB"
            pssKb < 400 * 1024 -> "200-400MB"
            pssKb < 800 * 1024 -> "400-800MB"
            else -> "800MB+"
        }
    }
}
