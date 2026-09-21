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
 * Emits the `ddg.pageLoad` async trace section. Real implementation ships only in the internal
 * flavor (only readable via a profileable/shell trace); Play/F-Droid get a no-op.
 */
interface PageLoadTracer {
    /** Begins an async section and returns a process-unique cookie to pass back to [endAsyncSection]. */
    fun beginAsyncSection(name: String): Int
    fun endAsyncSection(name: String, cookie: Int)
}

/**
 * Brackets each real http(s) main-frame page load with the `ddg.pageLoad` async trace section,
 * closing any section left open by a non-completing load. One instance per WebViewClient; cookie
 * uniqueness across clients comes from the shared [tracer].
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
        // A non-http finish (about:blank, duck://) must not close the open http section, or its
        // slice stretches to the wrong endpoint; a stuck section closes on the next onPageStarted.
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
