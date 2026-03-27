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

import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.bridge.impl.DuckAiBridgeFilesDir
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.bridge.impl.store.DuckAiBridgeSettingsDao
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import java.io.File
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

@ContributesMultibinding(AppScope::class)
class DuckAiNativeStorageJsMessageHandler @Inject constructor(
    private val settingsDao: DuckAiBridgeSettingsDao,
    private val chatsDao: DuckAiBridgeChatsDao,
    @DuckAiBridgeFilesDir private val filesDirLazy: Lazy<File>,
    private val duckAiHostProvider: DuckAiHostProvider,
) : ContentScopeJsMessageHandlersPlugin {

    private val filesDir: File get() = filesDirLazy.get()

    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override val featureName = "duckAiNativeStorage"
            override val allowedDomains = listOf(duckAiHostProvider.getHost())
            override val methods = listOf(
                // Settings
                "getSetting", "putSetting", "getAllSettings", "replaceAllSettings", "deleteSetting", "deleteAllSettings",
                // Chats
                "getAllChats", "putChat", "deleteChat", "deleteAllChats",
                // Migration
                "isMigrationDone", "markMigrationDone",
                // Images
                "getFile", "putFile", "deleteFile", "deleteAllFiles", "listFiles",
            )

            // Runs on the JavaBridge thread — DAO/file I/O are safe here.
            override fun process(jsMessage: JsMessage, jsMessaging: JsMessaging, jsMessageCallback: JsMessageCallback?) {
                when (jsMessage.method) {
                    // --- Settings ---
                    "getSetting" -> {
                        val key = jsMessage.params.optString("key")
                        val value = if (key.isBlank()) null else settingsDao.get(key)?.value
                        jsMessage.id?.let { id ->
                            jsMessaging.onResponse(
                                JsCallbackData(
                                    featureName = featureName,
                                    method = jsMessage.method,
                                    id = id,
                                    params = JSONObject().put("value", value ?: JSONObject.NULL),
                                ),
                            )
                        }
                    }
                    "putSetting" -> {
                        val key = jsMessage.params.optString("key")
                        val value = jsMessage.params.optString("value")
                        if (key.isNotBlank()) settingsDao.upsert(DuckAiBridgeSettingEntity(key = key, value = value))
                    }
                    "getAllSettings" -> {
                        val settings = JSONObject()
                        settingsDao.getAll().forEach { settings.put(it.key, it.value) }
                        jsMessage.id?.let { id ->
                            jsMessaging.onResponse(
                                JsCallbackData(
                                    featureName = featureName,
                                    method = jsMessage.method,
                                    id = id,
                                    params = JSONObject().put("settings", settings),
                                ),
                            )
                        }
                    }
                    "replaceAllSettings" -> {
                        val settingsObj = jsMessage.params.optJSONObject("settings") ?: return
                        val entities = settingsObj.keys().asSequence()
                            .map { k -> DuckAiBridgeSettingEntity(key = k, value = settingsObj.getString(k)) }
                            .toList()
                        settingsDao.replaceAll(entities)
                    }
                    "deleteSetting" -> {
                        val key = jsMessage.params.optString("key")
                        if (key.isNotBlank()) settingsDao.delete(key)
                    }
                    "deleteAllSettings" -> settingsDao.deleteAll()

                    // --- Chats ---
                    "getAllChats" -> {
                        val array = JSONArray()
                        chatsDao.getAll().forEach { array.put(JSONObject(it.data)) }
                        jsMessage.id?.let { id ->
                            jsMessaging.onResponse(
                                JsCallbackData(
                                    featureName = featureName,
                                    method = jsMessage.method,
                                    id = id,
                                    params = JSONObject().put("chats", array),
                                ),
                            )
                        }
                    }
                    "putChat" -> {
                        val chatId = jsMessage.params.optString("chatId")
                        val dataObj = jsMessage.params.optJSONObject("data") ?: return
                        if (chatId.isNotBlank()) chatsDao.upsert(DuckAiBridgeChatEntity(chatId = chatId, data = dataObj.toString()))
                    }
                    "deleteChat" -> {
                        val chatId = jsMessage.params.optString("chatId")
                        if (chatId.isNotBlank()) chatsDao.delete(chatId)
                    }
                    "deleteAllChats" -> chatsDao.deleteAll()

                    // --- Migration ---
                    "isMigrationDone" -> {
                        val done = settingsDao.get(MIGRATION_KEY) != null
                        jsMessage.id?.let { id ->
                            jsMessaging.onResponse(
                                JsCallbackData(
                                    featureName = featureName,
                                    method = jsMessage.method,
                                    id = id,
                                    params = JSONObject().put("value", done),
                                ),
                            )
                        }
                    }
                    "markMigrationDone" -> settingsDao.upsert(DuckAiBridgeSettingEntity(key = MIGRATION_KEY, value = "true"))

                    // --- Images ---
                    "getFile" -> {
                        val uuid = jsMessage.params.optString("uuid")
                        val json = if (isValidUuid(uuid)) {
                            val file = File(filesDir, uuid)
                            if (file.exists()) file.readText() else null
                        } else null
                        jsMessage.id?.let { id ->
                            jsMessaging.onResponse(
                                JsCallbackData(
                                    featureName = featureName,
                                    method = jsMessage.method,
                                    id = id,
                                    params = json?.let { JSONObject(it) } ?: JSONObject().put("value", JSONObject.NULL),
                                ),
                            )
                        }
                    }
                    "putFile" -> {
                        val uuid = jsMessage.params.optString("uuid")
                        if (isValidUuid(uuid)) {
                            filesDir.mkdirs()
                            File(filesDir, uuid).writeText(jsMessage.params.toString())
                        }
                    }
                    "deleteFile" -> {
                        val uuid = jsMessage.params.optString("uuid")
                        if (isValidUuid(uuid)) File(filesDir, uuid).delete()
                    }
                    "deleteAllFiles" -> filesDir.listFiles()?.forEach { it.delete() }
                    "listFiles" -> {
                        val array = JSONArray()
                        filesDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
                            runCatching { JSONObject(file.readText()) }.getOrNull()?.let { json ->
                                array.put(
                                    JSONObject()
                                        .put("uuid", json.optString("uuid", file.name))
                                        .put("chatId", json.optString("chatId", ""))
                                        .put("dataSize", json.optString("data", "").length),
                                )
                            }
                        }
                        jsMessage.id?.let { id ->
                            jsMessaging.onResponse(
                                JsCallbackData(
                                    featureName = featureName,
                                    method = jsMessage.method,
                                    id = id,
                                    params = JSONObject().put("files", array),
                                ),
                            )
                        }
                    }
                }
            }
        }

    private fun isValidUuid(uuid: String): Boolean {
        if (uuid.contains('/') || uuid.contains('\\') || uuid.contains("..")) return false
        val candidate = File(filesDir, uuid).canonicalFile
        return candidate.parentFile?.canonicalFile == filesDir.canonicalFile
    }

    companion object {
        internal const val MIGRATION_KEY = "__duckAiMigrationDone"
    }
}
