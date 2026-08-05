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

class FakePerfTracer(
    private val enabled: Boolean = true,
) : PerfTracer {
    data class AsyncEvent(
        val name: String,
        val cookie: Int,
        val begin: Boolean,
    )

    val syncSections = mutableListOf<String>()
    val asyncEvents = mutableListOf<AsyncEvent>()
    private var nextCookie = 0

    override fun isEnabled(): Boolean = enabled

    override fun beginSection(name: String) {
        syncSections.add(name)
    }

    override fun endSection() = Unit

    override fun beginAsyncSection(name: String): Int {
        val cookie = nextCookie++
        asyncEvents.add(AsyncEvent(name, cookie, begin = true))
        return cookie
    }

    override fun endAsyncSection(
        name: String,
        cookie: Int,
    ) {
        asyncEvents.add(AsyncEvent(name, cookie, begin = false))
    }
}
