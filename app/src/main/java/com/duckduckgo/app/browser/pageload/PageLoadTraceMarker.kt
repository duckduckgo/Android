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

/**
 * Emits the `ddg.pageLoad` async trace section. The real implementation (androidx.tracing) ships
 * only in the internal flavor; Play/F-Droid get a no-op binding and never link the tracing library
 * (this instrumentation is only readable via a profileable/shell trace, which is internal-only).
 *
 * A single [AppScope] instance is shared across every [PageLoadTraceMarker] (i.e. every tab), so the
 * cookies it hands out are process-unique — two tabs loading concurrently can't collide on the same
 * (section, cookie) pair and corrupt each other's slices.
 */
interface PageLoadTracer {
    /** Begins an async section and returns a process-unique cookie to pass back to [endAsyncSection]. */
    fun beginAsyncSection(name: String): Int
    fun endAsyncSection(name: String, cookie: Int)
}

/**
 * Brackets each real http(s) main-frame page load with the `ddg.pageLoad` async trace section,
 * closing any section left open by a non-completing load. One instance per WebViewClient (holds the
 * per-client open-cookie state); cookie uniqueness across clients is guaranteed by the shared [tracer].
 *
 * This section is read offline by Perfetto `trace_processor` in the page-load CI benchmark; it is
 * NOT read by macrobenchmark's `TraceSectionMetric` (whose per-iteration windowing was unreliable).
 */
class PageLoadTraceMarker(
    private val tracer: PageLoadTracer,
) {
    private var openCookie: Int? = null

    fun onPageStarted(url: String?) {
        if (!url.isHttp()) return
        openCookie?.let { tracer.endAsyncSection(SECTION, it) } // close any stuck section
        openCookie = tracer.beginAsyncSection(SECTION)
    }

    fun onPageFinished(url: String?, progress: Int) {
        if (progress != 100) return
        // Same http(s) guard as onPageStarted: a non-http finish (e.g. about:blank, duck://) must
        // NOT close the http section currently open, or that load's slice gets stretched to the
        // wrong endpoint. A genuinely stuck section is closed by the next onPageStarted instead.
        if (!url.isHttp()) return
        openCookie?.let {
            tracer.endAsyncSection(SECTION, it)
            openCookie = null
        }
    }

    private fun String?.isHttp() = this != null && (startsWith("http://") || startsWith("https://"))

    companion object {
        const val SECTION = "ddg.pageLoad"
    }
}
