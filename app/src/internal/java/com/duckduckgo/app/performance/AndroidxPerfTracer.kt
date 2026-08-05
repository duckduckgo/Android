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

package com.duckduckgo.app.performance

import androidx.tracing.Trace
import com.duckduckgo.common.utils.performance.PerfTracer
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidxPerfTracer @Inject constructor() : PerfTracer {

    private val cookieSeq = AtomicInteger(0)

    override fun isEnabled(): Boolean = Trace.isEnabled()

    override fun beginSection(name: String) = Trace.beginSection(name)

    override fun endSection() = Trace.endSection()

    override fun beginAsyncSection(name: String): Int {
        val cookie = cookieSeq.getAndIncrement()
        Trace.beginAsyncSection(name, cookie)
        return cookie
    }

    override fun endAsyncSection(
        name: String,
        cookie: Int,
    ) = Trace.endAsyncSection(name, cookie)

    override fun counter(
        name: String,
        value: Int,
    ) = Trace.setCounter(name, value)
}
