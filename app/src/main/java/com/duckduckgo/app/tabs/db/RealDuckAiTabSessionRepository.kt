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

package com.duckduckgo.app.tabs.db

import androidx.core.net.toUri
import com.duckduckgo.app.tabs.model.DuckAiTabSessionRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDuckAiTabSessionRepository @Inject constructor(
    private val dao: DuckAiTabSessionDao,
    // Lazy because RealDuckChat itself depends on this repository to set the pending entry point.
    private val duckChat: Lazy<DuckChat>,
) : DuckAiTabSessionRepository {

    private val pendingEntryPointSource = AtomicReference<String?>()

    override fun setPendingEntryPointSource(source: String) {
        pendingEntryPointSource.set(source)
    }

    override fun tryClaimEntryPointSource(tabId: String, url: String?) {
        val source = pendingEntryPointSource.get() ?: return
        if (url == null || !duckChat.get().isDuckChatUrl(url.toUri())) return
        // Only the call that actually matches consumes the pending value, so an unrelated tab
        // creation/navigation racing in between never steals or clears it from the real target.
        if (!pendingEntryPointSource.compareAndSet(source, null)) return
        runCatching { dao.insertOrReplace(DuckAiTabSessionEntity(tabId = tabId, entryPointSource = source)) }
    }

    override suspend fun getEntryPointSource(tabId: String): String? = dao.getEntryPointSource(tabId)
}
