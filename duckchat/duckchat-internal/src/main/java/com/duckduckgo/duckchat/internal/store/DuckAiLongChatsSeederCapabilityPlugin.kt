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

package com.duckduckgo.duckchat.internal.store

import android.content.Context
import android.widget.Toast
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.internal.DuckAiDevCapabilityPlugin
import com.duckduckgo.duckchat.store.impl.DuckAiMigrationPrefs
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Seeds Regular-mode native storage with long chats, each assistant reply split into many parts, and opens each in a
 * new tab. Every run adds a new batch.
 */
@ContributesMultibinding(AppScope::class)
class DuckAiLongChatsSeederCapabilityPlugin @Inject constructor(
    @param:RegularMode private val chatsDao: DuckAiBridgeChatsDao,
    @param:RegularMode private val tabRepository: TabRepository,
    private val migrationPrefs: DuckAiMigrationPrefs,
    private val duckChat: DuckChatInternal,
    private val dispatchers: DispatcherProvider,
    @param:AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckAiDevCapabilityPlugin {

    override fun title(): String = "Seed long chats"

    override fun subtitle(): String = "Adds ${TURNS_PER_CHAT.size} long chats to native storage and opens each in a tab"

    override fun onCapabilityClicked(activityContext: Context) {
        val appContext = activityContext.applicationContext
        appCoroutineScope.launch(dispatchers.io()) {
            val message = if (migrationPrefs.isMigrationDone(DuckAiMigrationPrefs.CHATS_KEY)) {
                seed()
                "Seeded ${TURNS_PER_CHAT.size} chats and opened them in new tabs"
            } else {
                // Seeding before the FE migrates would leave its own chats behind once migration is marked done.
                "Native storage not migrated yet: open Duck.ai once, then try again"
            }
            withContext(dispatchers.main()) {
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun seed() {
        TURNS_PER_CHAT.forEachIndexed { index, turns ->
            val chatId = UUID.randomUUID().toString()
            chatsDao.upsert(DuckAiBridgeChatEntity(chatId = chatId, data = longChat(chatId, index, turns).toString()))
            tabRepository.add(url = duckChat.buildChatUrl(chatId), skipHome = true)
        }
    }

    private fun longChat(chatId: String, index: Int, turns: Int): JSONObject {
        val now = Instant.now().toString()
        val messages = JSONArray()
        repeat(turns) { turn ->
            messages.put(
                JSONObject()
                    .put("role", "user")
                    .put("createdAt", now)
                    .put("content", "Question ${turn + 1}: tell me more about part ${turn + 1}."),
            )
            messages.put(
                JSONObject()
                    .put("role", "assistant")
                    .put("createdAt", now)
                    .put("content", "")
                    .put("model", MODEL)
                    .put("status", "active")
                    .put("origin", "text")
                    .put("parts", assistantParts(turn)),
            )
        }
        return JSONObject()
            .put("chatId", chatId)
            .put("title", "Long chat ${index + 1} ($turns turns) ${chatId.take(4)}")
            .put("model", MODEL)
            .put("lastEdit", now)
            .put("pinned", false)
            .put("messages", messages)
    }

    private fun assistantParts(turn: Int): JSONArray {
        val parts = JSONArray()
        parts.put(JSONObject().put("type", "reasoning").put("state", "done").put("summaryText", JSONArray()))
        repeat(TEXT_PARTS_PER_REPLY) { part ->
            parts.put(JSONObject().put("type", "text").put("text", "Answer ${turn + 1}, part ${part + 1}. $FILLER"))
        }
        return parts
    }

    private companion object {
        // Keeps the largest chat around 1MB, under the 2MB CursorWindow limit that every chat read shares.
        val TURNS_PER_CHAT = listOf(10, 50, 150, 300, 400)
        const val TEXT_PARTS_PER_REPLY = 5
        const val MODEL = "gpt-5-mini"
        const val FILLER = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et " +
            "dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea " +
            "commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla " +
            "pariatur."
    }
}
