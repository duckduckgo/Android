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

package com.duckduckgo.duckchat.localserver.impl.handler

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.localserver.impl.DuckAiRequestHandler
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiChatEntity
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiChatsDao
import com.squareup.anvil.annotations.ContributesMultibinding
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ChatsHandler @Inject constructor(
    private val dao: DuckAiChatsDao,
) : DuckAiRequestHandler {

    override val pathPrefix = "/chats"

    override fun handle(method: Method, uri: String, body: String?): Response {
        return if (uri == "/chats") {
            handleCollection(method)
        } else {
            val chatId = uri.removePrefix("/chats/").takeIf { it.isNotBlank() }
            handleSingle(method, chatId, body)
        }
    }

    private fun handleCollection(method: Method): Response {
        return when (method) {
            Method.GET -> handleGetAll()
            Method.DELETE -> handleDeleteAll()
            else -> NanoHTTPD.newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "Method not allowed")
        }
    }

    private fun handleSingle(method: Method, chatId: String?, body: String?): Response {
        return when (method) {
            Method.PUT -> handlePut(chatId, body)
            Method.DELETE -> handleDelete(chatId)
            else -> NanoHTTPD.newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "Method not allowed")
        }
    }

    private fun handleGetAll(): Response {
        val array = JSONArray()
        dao.getAll().forEach { array.put(JSONObject(it.data)) }
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", array.toString())
    }

    private fun handlePut(chatId: String?, body: String?): Response {
        if (chatId.isNullOrBlank() || body.isNullOrBlank()) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "chatId and body required")
        }
        try {
            JSONObject(body)
        } catch (e: Exception) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Body must be a JSON object")
        }
        dao.upsert(DuckAiChatEntity(chatId = chatId, data = body))
        return NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
    }

    private fun handleDelete(chatId: String?): Response {
        if (chatId.isNullOrBlank()) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "chatId required")
        }
        dao.delete(chatId)
        return NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
    }

    private fun handleDeleteAll(): Response {
        dao.deleteAll()
        return NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
    }
}
