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

package com.duckduckgo.duckchat.bridge.impl.handler

import androidx.webkit.JavaScriptReplyProxy
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.bridge.impl.DuckAiBridgeHandler
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeChatsDao
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exposes chat storage to duck.ai JS as `window.ChatsBridge`.
 *
 * DAO calls are dispatched to [dispatchers.io()] — [addWebMessageListener] fires on the main thread.
 * Business logic is in `internal` functions so tests can call them directly.
 */
@ContributesMultibinding(AppScope::class)
class ChatsBridgeHandler @Inject constructor(
    private val dao: DuckAiBridgeChatsDao,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : DuckAiBridgeHandler {

    override val bridgeName = "ChatsBridge"

    override fun onMessage(message: String, replyProxy: JavaScriptReplyProxy) {
        val json = try { JSONObject(message) } catch (e: Exception) { return }
        appScope.launch(dispatchers.io()) {
            when (json.optString("action")) {
                "getAllChats" -> {
                    val result = JSONObject().apply {
                        put("action", "getAllChats")
                        put("value", handleGetAll())
                    }
                    replyProxy.postMessage(result.toString())
                }
                "putChat" -> json.optJSONObject("data")?.toString()?.let { handlePut(json.optString("chatId"), it) }
                "deleteChat" -> handleDelete(json.optString("chatId"))
                "deleteAllChats" -> handleDeleteAll()
            }
        }
    }

    internal fun handleGetAll(): String {
        val array = JSONArray()
        dao.getAll().forEach { array.put(JSONObject(it.data)) }
        return array.toString()
    }

    internal fun handlePut(chatId: String, json: String) {
        if (chatId.isBlank()) return
        try { JSONObject(json) } catch (e: Exception) { return }
        dao.upsert(DuckAiBridgeChatEntity(chatId = chatId, data = json))
    }

    internal fun handleDelete(chatId: String) {
        if (chatId.isBlank()) return
        dao.delete(chatId)
    }

    internal fun handleDeleteAll() {
        dao.deleteAll()
    }
}
