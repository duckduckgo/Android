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

import androidx.annotation.MainThread
import com.duckduckgo.app.browser.WebViewPixelName.WEB_RENDERER_GONE_CRASH
import com.duckduckgo.app.browser.WebViewPixelName.WEB_RENDERER_GONE_KILLED
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.startup.metrics.MemoryCollector
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface RenderProcessGoneReporter {
    @MainThread
    fun onRenderProcessGone(didCrash: Boolean)
}

/**
 * WebViews in the same profile share a renderer process, and when it dies Android calls onRenderProcessGone on each of
 * them, one after another within a single main-thread message. Firing per callback counted one death once per live tab,
 * so this collects the callbacks delivered before the next main-thread message and fires a single pixel for them.
 */
@ContributesIntoSet(AppScope::class, binding = binding<BrowserLifecycleObserver>())
@ContributesBinding(AppScope::class, binding = binding<RenderProcessGoneReporter>())
@SingleIn(AppScope::class)
class RealRenderProcessGoneReporter @Inject constructor(
    private val pixel: Pixel,
    private val memoryCollector: MemoryCollector,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : RenderProcessGoneReporter, BrowserLifecycleObserver {

    private var appOpen = false
    private var pendingDeath: RendererDeath? = null

    override fun onOpen(isFreshLaunch: Boolean) {
        appOpen = true
    }

    override fun onClose() {
        appOpen = false
    }

    override fun onRenderProcessGone(didCrash: Boolean) {
        pendingDeath?.let {
            it.affectedWebViews++
            return
        }
        pendingDeath = RendererDeath(didCrash = didCrash, appOpen = appOpen)
        // Dispatchers.Main posts rather than running inline, so this runs once the current message has notified every WebView.
        appCoroutineScope.launch(dispatchers.main()) {
            pendingDeath?.let(::fire)
            pendingDeath = null
        }
    }

    private fun fire(death: RendererDeath) {
        val params = buildMap {
            put(PARAM_APP_STATE, if (death.appOpen) "foreground" else "background")
            put(PARAM_AFFECTED_WEBVIEWS, bucketWebViewCount(death.affectedWebViews))
            memoryCollector.collectDeviceRamBucket()?.let { put(PARAM_DEVICE_RAM, it) }
        }
        pixel.fire(if (death.didCrash) WEB_RENDERER_GONE_CRASH else WEB_RENDERER_GONE_KILLED, params)
    }

    private class RendererDeath(
        val didCrash: Boolean,
        val appOpen: Boolean,
        var affectedWebViews: Int = 1,
    )

    companion object {
        const val PARAM_APP_STATE = "app_state"
        const val PARAM_AFFECTED_WEBVIEWS = "affected_webviews"
        const val PARAM_DEVICE_RAM = "device_ram_bucket"

        private fun bucketWebViewCount(count: Int): String = when {
            count <= 1 -> "1"
            count <= 3 -> "2-3"
            count <= 5 -> "4-5"
            count <= 10 -> "6-10"
            else -> "11+"
        }
    }
}
