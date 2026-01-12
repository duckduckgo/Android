/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.sync

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.api.DuckAiChatDeletionListener
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class, boundType = DuckAiChatDeletionListener::class)
@ContributesMultibinding(AppScope::class, boundType = MainProcessLifecycleObserver::class)
class DuckAiChatDeletionListenerImpl @Inject constructor(
    private val duckChatSyncRepository: DuckChatSyncRepository,
) : DuckAiChatDeletionListener, MainProcessLifecycleObserver {

    @Volatile
    private var appBackgroundedTimestamp: Long? = null

    override fun onStop(owner: LifecycleOwner) {
        appBackgroundedTimestamp = System.currentTimeMillis()
        logcat { "DuckChat-Sync: App went to background, stored timestamp: $appBackgroundedTimestamp" }
    }

    override fun onStart(owner: LifecycleOwner) {
        appBackgroundedTimestamp = null
        logcat { "DuckChat-Sync: App came to foreground, cleared background timestamp" }
    }

    override suspend fun onDuckAiChatsDeleted() {
        val timestamp = appBackgroundedTimestamp ?: System.currentTimeMillis()
        logcat { "DuckChat-Sync: Duck AI chats deleted, using timestamp: $timestamp (background: ${appBackgroundedTimestamp != null})" }
        duckChatSyncRepository.recordDuckAiChatsDeleted(timestamp)
    }
}
