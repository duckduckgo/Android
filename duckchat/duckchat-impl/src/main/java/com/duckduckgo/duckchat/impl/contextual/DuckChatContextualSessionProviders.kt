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

package com.duckduckgo.duckchat.impl.contextual

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface DuckChatContextualSessionTimeoutProvider {
    fun sessionTimeoutMillis(): Long
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDuckChatContextualSessionTimeoutProvider @Inject constructor(
    private val duckChatInternal: DuckChatInternal,
) : DuckChatContextualSessionTimeoutProvider {
    override fun sessionTimeoutMillis(): Long {
        return duckChatInternal.keepSessionIntervalInMinutes() * MINUTES_TO_MS
    }

    private companion object {
        private const val MINUTES_TO_MS = 60_000L
    }
}

interface DuckChatContextualTimeProvider {
    fun currentTimeMillis(): Long
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SystemDuckChatContextualTimeProvider @Inject constructor() : DuckChatContextualTimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
