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

package com.duckduckgo.duckchat.localserver.impl

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.localserver.api.DuckAiChatStorage
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiChatsDao
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.json.JSONObject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckAiChatStorage @Inject constructor(
    private val chatsDao: DuckAiChatsDao,
    @DuckAiImagesDir private val imagesDirLazy: Lazy<File>,
    private val dispatchers: DispatcherProvider,
) : DuckAiChatStorage {

    override suspend fun deleteChat(chatId: String): Unit = withContext(dispatchers.io()) {
        chatsDao.delete(chatId)
        imagesDirLazy.get().listFiles()?.forEach { file ->
            runCatching { JSONObject(file.readText()) }.getOrNull()?.let { json ->
                if (json.optString("chatId") == chatId) file.delete()
            }
        }
    }

    override suspend fun deleteAllChats(): Unit = withContext(dispatchers.io()) {
        chatsDao.deleteAll()
        imagesDirLazy.get().listFiles()?.forEach { it.delete() }
    }
}
