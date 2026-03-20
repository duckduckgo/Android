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
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiSettingEntity
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiSettingsDao
import com.squareup.anvil.annotations.ContributesMultibinding
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class SettingsHandler @Inject constructor(
    private val dao: DuckAiSettingsDao,
) : DuckAiRequestHandler {

    override val pathPrefix = "/settings"

    override fun handle(method: Method, uri: String, body: String?): Response {
        return if (uri == "/settings") {
            handleCollection(method, body)
        } else {
            val key = uri.removePrefix("/settings/").takeIf { it.isNotBlank() }
            handleSingle(method, key, body)
        }
    }

    private fun handleCollection(method: Method, body: String?): Response {
        return when (method) {
            Method.GET -> handleGetAll()
            Method.PUT -> handleReplaceAll(body)
            Method.DELETE -> handleDeleteAll()
            else -> NanoHTTPD.newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "Method not allowed")
        }
    }

    private fun handleSingle(method: Method, key: String?, body: String?): Response {
        return when (method) {
            Method.GET -> handleGetOne(key)
            Method.PUT -> handlePut(key, body)
            Method.DELETE -> handleDelete(key)
            else -> NanoHTTPD.newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "Method not allowed")
        }
    }

    // GET /settings/{key} — returns JSON string literal or 404
    private fun handleGetOne(key: String?): Response {
        if (key.isNullOrBlank()) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Key required")
        }
        val entity = dao.get(key)
            ?: return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not found")
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", toJsonString(entity.value))
    }

    // GET /settings — returns JSON object of all pairs
    private fun handleGetAll(): Response {
        val json = JSONObject()
        dao.getAll().forEach { json.put(it.key, it.value) }
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", json.toString())
    }

    // PUT /settings/{key} — body is JSON string literal, e.g. "dark"
    private fun handlePut(key: String?, body: String?): Response {
        if (key.isNullOrBlank() || body.isNullOrBlank()) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Key and body required")
        }
        val value = parseJsonString(body)
            ?: return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Body must be a JSON string")
        dao.upsert(DuckAiSettingEntity(key = key, value = value))
        return NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
    }

    // PUT /settings — body is JSON object, replaces ALL settings atomically
    private fun handleReplaceAll(body: String?): Response {
        if (body.isNullOrBlank()) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Body required")
        }
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Body must be a JSON object")
        }
        val entities = json.keys().asSequence().map { k ->
            DuckAiSettingEntity(key = k, value = json.getString(k))
        }.toList()
        dao.replaceAll(entities)
        return NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
    }

    // DELETE /settings/{key}
    private fun handleDelete(key: String?): Response {
        if (key.isNullOrBlank()) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Key required")
        }
        // Idempotent DELETE: returns 204 whether or not the key existed.
        // The client only needs to know the key is gone, not whether it was previously present.
        dao.delete(key)
        return NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
    }

    // DELETE /settings — full reset
    private fun handleDeleteAll(): Response {
        dao.deleteAll()
        return NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
    }

    // Serialize a raw string value as a JSON string literal.
    // e.g. "dark" → "\"dark\"", "say \"hi\"" → "\"say \\\"hi\\\"\""
    // Uses JSONArray to handle all escaping correctly.
    private fun toJsonString(value: String): String =
        JSONArray().put(value).toString().removeSurrounding("[", "]")

    // Parse a JSON string literal body into a raw string value.
    // e.g. "\"dark\"" → "dark"
    // Requires the body to be a properly quoted JSON string (starts and ends with '"').
    private fun parseJsonString(body: String): String? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("\"") || !trimmed.endsWith("\"")) return null
        return try {
            org.json.JSONTokener(trimmed).nextValue() as? String
        } catch (e: Exception) {
            null
        }
    }
}
