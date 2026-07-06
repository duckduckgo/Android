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

import androidx.tracing.Trace

/** Seam so the cookie sequencing can be unit-tested without the android tracing runtime. */
interface PageLoadTracer {
    fun beginAsyncSection(name: String, cookie: Int)
    fun endAsyncSection(name: String, cookie: Int)
}

class AndroidxPageLoadTracer : PageLoadTracer {
    override fun beginAsyncSection(name: String, cookie: Int) = Trace.beginAsyncSection(name, cookie)
    override fun endAsyncSection(name: String, cookie: Int) = Trace.endAsyncSection(name, cookie)
}

/**
 * Brackets each real http(s) main-frame page load with the `ddg.pageLoad` async trace section,
 * using a unique cookie per load and closing any section left open by a non-completing load.
 * One instance per WebViewClient (holds per-client mutable state).
 *
 * This section is read offline by Perfetto `trace_processor` in the page-load CI benchmark; it is
 * NOT read by macrobenchmark's `TraceSectionMetric` (whose per-iteration windowing was unreliable).
 */
class PageLoadTraceMarker(
    private val tracer: PageLoadTracer = AndroidxPageLoadTracer(),
) {
    private var openCookie: Int? = null
    private var cookieSeq: Int = 0

    fun onPageStarted(url: String?) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return
        openCookie?.let { tracer.endAsyncSection(SECTION, it) } // close any stuck section
        val cookie = cookieSeq++
        openCookie = cookie
        tracer.beginAsyncSection(SECTION, cookie)
    }

    fun onPageFinished(url: String?, progress: Int) {
        if (progress != 100) return
        if (url == null || url == "about:blank") return
        openCookie?.let {
            tracer.endAsyncSection(SECTION, it)
            openCookie = null
        }
    }

    companion object {
        const val SECTION = "ddg.pageLoad"
    }
}
