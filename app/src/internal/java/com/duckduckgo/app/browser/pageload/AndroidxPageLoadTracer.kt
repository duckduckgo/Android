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
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Internal-flavor implementation of [PageLoadTracer]. Single app-scoped instance so its cookie
 * counter is shared across all [PageLoadTraceMarker]s (one per tab) and stays process-unique.
 */
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidxPageLoadTracer @Inject constructor() : PageLoadTracer {

    private val cookieSeq = AtomicInteger(0)

    override fun beginAsyncSection(name: String): Int {
        val cookie = cookieSeq.getAndIncrement()
        Trace.beginAsyncSection(name, cookie)
        return cookie
    }

    override fun endAsyncSection(name: String, cookie: Int) = Trace.endAsyncSection(name, cookie)
}
