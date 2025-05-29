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

package com.duckduckgo.app.browser.pageloadpixel.firstpaint

import android.webkit.WebView
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedSites.Companion.sites
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.logcat

interface PagePaintedHandler {
    operator fun invoke(
        webView: WebView,
        url: String,
    )
}

@ContributesBinding(AppScope::class)
class RealPagePaintedHandler @Inject constructor(
    private val deviceInfo: DeviceInfo,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val dao: PagePaintedPixelDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : PagePaintedHandler {

    private val job = ConflatedJob()

    override operator fun invoke(
        webView: WebView,
        url: String,
    ) {
        job += appCoroutineScope.launch(dispatcherProvider.io()) {
            if (sites.any { UriString.sameOrSubdomain(url, it) }) {
                kotlin.runCatching {
                    val firstPaint = webView.extractPagePaintDurations().toDoubleOrNull()?.roundToLong() ?: return@launch
                    dao.add(
                        PagePaintedPixelEntity(
                            appVersion = deviceInfo.appVersion,
                            webViewVersion = webViewVersionProvider.getMajorVersion(),
                            elapsedTimeFirstPaint = firstPaint,
                        ),
                    )

                    logcat(VERBOSE) { "First-paint duration extracted: ${firstPaint}ms for $url" }
                }
            }
        }
    }

    private suspend fun WebView.extractPagePaintDurations(): String {
        return withContext(dispatcherProvider.main()) {
            suspendCoroutine { continuation ->
                evaluateJavascript("javascript:$PAGE_PAINT_JS") { value ->
                    continuation.resume(value)
                }
            }
        }
    }

    companion object {
        private val PAGE_PAINT_JS = """(() => {
                    const paintResources = performance.getEntriesByType("paint");
                    const firstPaint = paintResources.find((entry) => entry.name === 'first-paint');
                    return firstPaint.startTime;
                    })()
        """.trimIndent()
    }
}
