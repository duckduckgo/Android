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

package com.duckduckgo.app.browser.pageload

import com.duckduckgo.app.browser.WebViewPixelName
import com.duckduckgo.app.browser.pageload.PageLoadTimeoutWatchdog.Phase
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Pixels for the page load timeout watchdog.
 */
interface PageLoadTimeoutPixels {
    /**
     * The watchdog showed the timeout error for a navigation that stalled in [phase].
     */
    fun fireTimeoutShown(phase: Phase)

    /**
     * The timed-out navigation later loaded on its own; [elapsedSinceTimeoutMs] is measured from the timeout,
     * not from the navigation start.
     */
    fun fireTimeoutRecovered(elapsedSinceTimeoutMs: Long)
}

@ContributesBinding(AppScope::class)
class RealPageLoadTimeoutPixels @Inject constructor(
    private val pixel: Pixel,
) : PageLoadTimeoutPixels {

    override fun fireTimeoutShown(phase: Phase) {
        pixel.fire(WebViewPixelName.WEB_PAGE_LOAD_TIMEOUT_SHOWN, mapOf(PARAM_PHASE to phase.pixelValue()))
    }

    override fun fireTimeoutRecovered(elapsedSinceTimeoutMs: Long) {
        pixel.fire(
            WebViewPixelName.WEB_PAGE_LOAD_TIMEOUT_RECOVERED,
            mapOf(PARAM_RECOVERED_AFTER to recoveredAfterBucket(elapsedSinceTimeoutMs)),
        )
    }

    private fun Phase.pixelValue() = when (this) {
        Phase.NOT_COMMITTED -> "not_committed"
        Phase.COMMITTED_NO_CONTENT -> "committed_no_content"
    }

    private fun recoveredAfterBucket(elapsedMs: Long): String = when {
        elapsedMs < 5_000 -> "0-5s"
        elapsedMs < 15_000 -> "5-15s"
        elapsedMs < 30_000 -> "15-30s"
        elapsedMs < 60_000 -> "30-60s"
        else -> "60s+"
    }

    private companion object {
        const val PARAM_PHASE = "phase"
        const val PARAM_RECOVERED_AFTER = "recovered_after"
    }
}
