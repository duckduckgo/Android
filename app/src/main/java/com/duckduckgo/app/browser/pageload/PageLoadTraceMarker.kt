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

import com.duckduckgo.common.utils.performance.PerfTracer

/**
 * Brackets each real http(s) main-frame page load with the `ddg.pageLoad` async trace section, and
 * emits a `ddg.pageCommitVisible` tick at first paint.
 *
 * One instance per WebViewClient — it holds that client's open-cookie state. Cookie uniqueness
 * across concurrently loading tabs comes from the app-scoped [tracer].
 */
class PageLoadTraceMarker(
    private val tracer: PerfTracer,
) {
    private var openCookie: Int? = null

    fun onPageStarted(url: String?) {
        if (!url.isHttp()) return
        openCookie?.let { tracer.endAsyncSection(PAGE_LOAD, it) }
        openCookie = tracer.beginAsyncSection(PAGE_LOAD)
    }

    fun onPageFinished(
        url: String?,
        progress: Int,
    ) {
        if (progress != PROGRESS_COMPLETE) return
        // A non-http finish (about:blank, duck://) must not close the http section currently open,
        // or that load's slice is stretched to the wrong endpoint. A genuinely stuck section is
        // closed by the next onPageStarted instead.
        if (!url.isHttp()) return
        openCookie?.let {
            tracer.endAsyncSection(PAGE_LOAD, it)
            openCookie = null
        }
    }

    fun onPageCommitVisible(url: String?) {
        if (!url.isHttp()) return
        tracer.endAsyncSection(PAGE_COMMIT_VISIBLE, tracer.beginAsyncSection(PAGE_COMMIT_VISIBLE))
    }

    private fun String?.isHttp() = this != null && (startsWith("http://") || startsWith("https://"))

    companion object {
        private const val PAGE_LOAD = "ddg.pageLoad"
        private const val PAGE_COMMIT_VISIBLE = "ddg.pageCommitVisible"
        private const val PROGRESS_COMPLETE = 100
    }
}
