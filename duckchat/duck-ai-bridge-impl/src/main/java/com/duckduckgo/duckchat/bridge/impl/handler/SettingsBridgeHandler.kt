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
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeSettingsDao
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Exposes settings storage to duck.ai JS as `window.SettingsBridge`.
 *
 * Setting values are stored as raw strings but exposed as JSON string literals to match
 * the HTTP `/settings` API contract (stored "dark" → JS receives `"dark"` including quotes).
 *
 * DAO calls are dispatched to [dispatchers.io()] — [addWebMessageListener] fires on the main thread.
 * Business logic is in `internal` functions so tests can call them directly.
 */
@ContributesMultibinding(AppScope::class)
class SettingsBridgeHandler @Inject constructor(
    private val dao: DuckAiBridgeSettingsDao,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : DuckAiBridgeHandler {

    override val bridgeName = "SettingsBridge"

    override fun onMessage(message: String, replyProxy: JavaScriptReplyProxy) {
        val json = try { JSONObject(message) } catch (e: Exception) { return }
        appScope.launch(dispatchers.io()) {
            when (json.optString("action")) {
                "getSetting" -> {
                    val value = handleGetOne(json.optString("key"))
                    val result = JSONObject().apply {
                        put("action", "getSetting")
                        put("value", value ?: JSONObject.NULL)
                    }
                    replyProxy.postMessage(result.toString())
                }
                "getAllSettings" -> {
                    val result = JSONObject().apply {
                        put("action", "getAllSettings")
                        put("value", handleGetAll())
                    }
                    replyProxy.postMessage(result.toString())
                }
                "putSetting" -> handlePut(json.optString("key"), json.optString("value"))
                "replaceAllSettings" -> json.optJSONObject("data")?.toString()?.let { handleReplaceAll(it) }
                "deleteSetting" -> handleDelete(json.optString("key"))
                "deleteAllSettings" -> handleDeleteAll()
            }
        }
    }

    internal fun handleGetOne(key: String): String? {
        if (key.isBlank()) return null
        val entity = dao.get(key) ?: return null
        return toJsonString(entity.value)
    }

    internal fun handleGetAll(): String {
        val json = JSONObject()
        dao.getAll().forEach { json.put(it.key, it.value) }
        return json.toString()
    }

    internal fun handlePut(key: String, jsonValue: String) {
        if (key.isBlank()) return
        val value = parseJsonString(jsonValue) ?: return
        dao.upsert(DuckAiBridgeSettingEntity(key = key, value = value))
    }

    internal fun handleReplaceAll(jsonObject: String) {
        val json = try { JSONObject(jsonObject) } catch (e: Exception) { return }
        val entities = json.keys().asSequence().map { k ->
            DuckAiBridgeSettingEntity(key = k, value = json.getString(k))
        }.toList()
        dao.replaceAll(entities)
    }

    internal fun handleDelete(key: String) {
        if (key.isBlank()) return
        dao.delete(key)
    }

    internal fun handleDeleteAll() {
        dao.deleteAll()
    }

    // Serialize a raw string value as a JSON string literal.
    // e.g. "dark" → "\"dark\"", handles all escaping via JSONArray.
    private fun toJsonString(value: String): String =
        JSONArray().put(value).toString().removeSurrounding("[", "]")

    // Parse a JSON string literal body into a raw string value.
    // e.g. "\"dark\"" → "dark". Returns null if not a valid JSON string.
    private fun parseJsonString(body: String): String? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("\"") || !trimmed.endsWith("\"")) return null
        return try { JSONTokener(trimmed).nextValue() as? String } catch (e: Exception) { null }
    }
}
