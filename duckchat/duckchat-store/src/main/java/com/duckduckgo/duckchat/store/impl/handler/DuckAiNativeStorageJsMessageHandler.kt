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

package com.duckduckgo.duckchat.store.impl.handler

import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeFilesDir
import com.duckduckgo.duckchat.store.impl.DuckAiMigrationPrefs
import com.duckduckgo.duckchat.store.impl.DuckAiNativeStoragePixels
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DuckAiNativeStorageJsMessageHandler @Inject constructor(
    private val settingsDao: DuckAiBridgeSettingsDao,
    private val chatsDao: DuckAiBridgeChatsDao,
    private val fileMetaDao: DuckAiBridgeFileMetaDao,
    @DuckAiBridgeFilesDir private val filesDirLazy: Lazy<File>,
    private val duckAiHostProvider: DuckAiHostProvider,
    private val migrationPrefs: DuckAiMigrationPrefs,
    private val pixels: DuckAiNativeStoragePixels,
) : ContentScopeJsMessageHandlersPlugin {

    private val filesDir: File get() = filesDirLazy.get()

    override fun getJsMessageHandler(): JsMessageHandler =
        object : JsMessageHandler {
            override val featureName = "duckAiNativeStorage"
            override val allowedDomains = listOf(duckAiHostProvider.getHost())
            override val methods = listOf(
                "getEntry", "putEntry", "getAllEntries", "replaceAllEntries", "deleteEntry", "deleteAllEntries",
                // Chats
                "migrateChats", // will replace putChats
                "getAllChats", "putChat", "putChats", "deleteChat", "deleteAllChats",
                // Migration
                "isMigrationDone", "markMigrationDone",
                // Images
                "getFile", "putFile", "deleteFile", "deleteAllFiles", "listFiles",
            )

            // Runs on the JavaBridge thread — DAO/file I/O are safe here.
            override fun process(jsMessage: JsMessage, jsMessaging: JsMessaging, jsMessageCallback: JsMessageCallback?) {
                when (jsMessage.method) {
                    // --- Entries ---
                    "getEntry" -> {
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
                    "putEntry" -> {
                        val key = jsMessage.params.optString("key")
                        val value = jsMessage.params.optString("value")
                        if (key.isNotBlank()) {
                            logcat { "DuckAiNativeStorage: putEntry key=$key value=${value.take(80)}" }
                            settingsDao.upsert(DuckAiBridgeSettingEntity(key = key, value = value))
                        }
                    }
                    "getAllEntries" -> {
                        val obj = JSONObject()
                        settingsDao.getAll().forEach { obj.put(it.key, it.value) }
                        jsMessage.id?.let { id ->
                            jsMessaging.onResponse(
                                JsCallbackData(
                                    featureName = featureName,
                                    method = jsMessage.method,
                                    id = id,
                                    params = JSONObject().put("entries", obj),
                                ),
                            )
                        }
                    }
                    "replaceAllEntries" -> {
                        val settingsObj = jsMessage.params.optJSONObject("entries") ?: return
                        val entities = settingsObj.keys().asSequence()
                            .map { k -> DuckAiBridgeSettingEntity(key = k, value = settingsObj.getString(k)) }
                            .toList()
                        if (entities.isNotEmpty()) {
                            logcat { "DuckAiNativeStorage: replaceAllEntries count=${entities.size}" }
                            settingsDao.replaceAll(entities)
                        } else {
                            logcat { "DuckAiNativeStorage: trying to replaceAllEntries with empty list, no-op" }
                        }
                    }
                    "deleteEntry" -> {
                        val key = jsMessage.params.optString("key")
                        if (key.isNotBlank()) {
                            logcat { "DuckAiNativeStorage: deleteEntry key=$key" }
                            settingsDao.delete(key)
                        }
                    }
                    "deleteAllEntries" -> {
                        logcat { "DuckAiNativeStorage: deleteAllEntries" }
                        settingsDao.deleteAll()
                    }

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
                        if (chatId.isNotBlank()) {
                            logcat { "DuckAiNativeStorage: putChat chatId=$chatId" }
                            chatsDao.upsert(DuckAiBridgeChatEntity(chatId = chatId, data = dataObj.toString()))
                        }
                    }
                    "putChats", "migrateChats" -> {
                        val array = jsMessage.params.optJSONArray("chats") ?: return
                        val entities = (0 until array.length()).mapNotNull { i ->
                            val obj = array.optJSONObject(i) ?: return@mapNotNull null
                            val chatId = obj.optString("chatId")
                            val dataObj = obj.optJSONObject("data") ?: return@mapNotNull null
                            if (chatId.isBlank()) {
                                null
                            } else {
                                DuckAiBridgeChatEntity(chatId = chatId, data = dataObj.toString())
                            }
                        }
                        logcat { "DuckAiNativeStorage: ${jsMessage.method} count=${entities.size}" }
                        if (entities.isNotEmpty()) chatsDao.upsertAll(entities)
                        jsMessage.id?.let { id ->
                            jsMessaging.onResponse(
                                JsCallbackData(
                                    featureName = featureName,
                                    method = jsMessage.method,
                                    id = id,
                                    params = JSONObject().put("success", true),
                                ),
                            )
                        }
                    }
                    "deleteChat" -> {
                        val chatId = jsMessage.params.optString("chatId")
                        if (chatId.isNotBlank()) {
                            logcat { "DuckAiNativeStorage: deleteChat chatId=$chatId" }
                            chatsDao.delete(chatId)
                        }
                    }
                    "deleteAllChats" -> {
                        logcat { "DuckAiNativeStorage: deleteAllChats" }
                        chatsDao.deleteAll()
                    }

                    // --- Migration ---
                    "isMigrationDone" -> {
                        val key = jsMessage.params.optString("key")
                        val done = if (key.isNotBlank()) migrationPrefs.isMigrationDone(key) else false
                        logcat { "DuckAiNativeStorage: isMigrationDone key=$key / done=$done" }
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
                    "markMigrationDone" -> {
                        val key = jsMessage.params.optString("key")
                        if (key.isNotBlank()) {
                            logcat { "DuckAiNativeStorage: markMigrationDone key=$key" }
                            migrationPrefs.markMigrationDone(key)
                            pixels.reportMigrationDone(key)
                        } else {
                            pixels.reportMigrationDoneBlankKey()
                        }
                    }

                    // --- Files ---
                    "getFile" -> {
                        val uuid = jsMessage.params.optString("uuid")
                        val json = if (isValidUuid(uuid)) {
                            val file = File(filesDir, uuid)
                            if (file.exists()) file.readText() else null
                        } else {
                            null
                        }
                        jsMessage.id?.let { id ->
                            val params = json?.let { runCatching { JSONObject(it) }.getOrNull() }
                                ?: JSONObject().put("value", JSONObject.NULL)
                            jsMessaging.onResponse(
                                JsCallbackData(
                                    featureName = featureName,
                                    method = jsMessage.method,
                                    id = id,
                                    params = params,
                                ),
                            )
                        }
                    }
                    "putFile" -> {
                        val uuid = jsMessage.params.optString("uuid")
                        if (isValidUuid(uuid)) {
                            val sizeBytes = jsMessage.params.toString().length
                            logcat { "DuckAiNativeStorage: putFile uuid=$uuid size=${sizeBytes}B" }
                            fileMetaDao.upsert(
                                DuckAiBridgeFileMetaEntity(
                                    uuid = uuid,
                                    chatId = jsMessage.params.optString("chatId"),
                                    fileName = jsMessage.params.optString("fileName"),
                                    mimeType = jsMessage.params.optString("mimeType"),
                                ),
                            )
                            filesDir.mkdirs()
                            File(filesDir, uuid).writeText(jsMessage.params.toString())
                        }
                    }
                    "deleteFile" -> {
                        val uuid = jsMessage.params.optString("uuid")
                        if (isValidUuid(uuid)) {
                            logcat { "DuckAiNativeStorage: deleteFile uuid=$uuid" }
                            File(filesDir, uuid).delete()
                            fileMetaDao.delete(uuid)
                        }
                    }
                    "deleteAllFiles" -> {
                        logcat { "DuckAiNativeStorage: deleteAllFiles" }
                        filesDir.listFiles()?.filter { isValidUuid(it.name) }?.forEach { it.delete() }
                        fileMetaDao.deleteAll()
                    }
                    "listFiles" -> {
                        val array = JSONArray()
                        fileMetaDao.getAll().forEach { meta ->
                            array.put(
                                JSONObject()
                                    .put("uuid", meta.uuid)
                                    .put("chatId", meta.chatId)
                                    .put("fileName", meta.fileName)
                                    .put("mimeType", meta.mimeType)
                                    .put("dataSize", File(filesDir, meta.uuid).length()),
                            )
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
}
