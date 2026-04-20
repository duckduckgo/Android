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
                "getChat", "getAllChats", "putChat", "putChats", "deleteChat", "deleteAllChats",
                // Migration
                "isMigrationDone", "markMigrationDone",
                // Images
                "getFile", "putFile", "deleteFile", "deleteFiles", "deleteAllFiles", "listFiles",
            )

            // Runs on the JavaBridge thread — DAO/file I/O are safe here.
            override fun process(jsMessage: JsMessage, jsMessaging: JsMessaging, jsMessageCallback: JsMessageCallback?) {
                when (jsMessage.method) {
                    // --- Entries ---
                    "getEntry" -> {
                        val key = jsMessage.params.optString("key")
                        try {
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
                        } catch (e: Exception) {
                            pixels.reportSettingsGetError()
                            jsMessage.id?.let { id ->
                                jsMessaging.onResponse(
                                    JsCallbackData(
                                        featureName = featureName,
                                        method = jsMessage.method,
                                        id = id,
                                        params = JSONObject().put("value", JSONObject.NULL),
                                    ),
                                )
                            }
                        }
                    }
                    "putEntry" -> {
                        val key = jsMessage.params.optString("key")
                        val value = jsMessage.params.optString("value")
                        if (key.isNotBlank()) {
                            logcat { "DuckAiNativeStorage: putEntry key=$key value=${value.take(80)}" }
                            try {
                                settingsDao.upsert(DuckAiBridgeSettingEntity(key = key, value = value))
                            } catch (e: Exception) {
                                pixels.reportSettingsPutError()
                            }
                        }
                    }
                    "getAllEntries" -> {
                        try {
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
                        } catch (e: Exception) {
                            pixels.reportSettingsGetError()
                            jsMessage.id?.let { id ->
                                jsMessaging.onResponse(
                                    JsCallbackData(
                                        featureName = featureName,
                                        method = jsMessage.method,
                                        id = id,
                                        params = JSONObject().put("entries", JSONObject()),
                                    ),
                                )
                            }
                        }
                    }
                    "replaceAllEntries" -> {
                        val settingsObj = jsMessage.params.optJSONObject("entries") ?: return
                        val entities = settingsObj.keys().asSequence()
                            .map { k -> DuckAiBridgeSettingEntity(key = k, value = settingsObj.getString(k)) }
                            .toList()
                        if (entities.isNotEmpty()) {
                            logcat { "DuckAiNativeStorage: replaceAllEntries count=${entities.size}" }
                            try {
                                settingsDao.replaceAll(entities)
                            } catch (e: Exception) {
                                pixels.reportSettingsPutError()
                            }
                        } else {
                            logcat { "DuckAiNativeStorage: trying to replaceAllEntries with empty list, no-op" }
                        }
                    }
                    "deleteEntry" -> {
                        val key = jsMessage.params.optString("key")
                        if (key.isNotBlank()) {
                            logcat { "DuckAiNativeStorage: deleteEntry key=$key" }
                            try {
                                settingsDao.delete(key)
                            } catch (e: Exception) {
                                pixels.reportSettingsDeleteError()
                            }
                        }
                    }
                    "deleteAllEntries" -> {
                        logcat { "DuckAiNativeStorage: deleteAllEntries" }
                        try {
                            settingsDao.deleteAll()
                        } catch (e: Exception) {
                            pixels.reportSettingsDeleteError()
                        }
                    }

                    // --- Chats ---
                    "getChat" -> {
                        val chatId = jsMessage.params.optString("chatId")
                        try {
                            val chat = if (chatId.isNotBlank()) chatsDao.getById(chatId) else null
                            jsMessage.id?.let { id ->
                                jsMessaging.onResponse(
                                    JsCallbackData(
                                        featureName = featureName,
                                        method = jsMessage.method,
                                        id = id,
                                        params = JSONObject().put(
                                            "chat",
                                            chat?.let { runCatching { JSONObject(it.data) }.getOrNull() } ?: JSONObject.NULL,
                                        ),
                                    ),
                                )
                            }
                        } catch (e: Exception) {
                            pixels.reportChatGetError()
                            jsMessage.id?.let { id ->
                                jsMessaging.onResponse(
                                    JsCallbackData(
                                        featureName = featureName,
                                        method = jsMessage.method,
                                        id = id,
                                        params = JSONObject().put("chat", JSONObject.NULL),
                                    ),
                                )
                            }
                        }
                    }
                    "getAllChats" -> {
                        try {
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
                        } catch (e: Exception) {
                            pixels.reportChatGetError()
                            jsMessage.id?.let { id ->
                                jsMessaging.onResponse(
                                    JsCallbackData(
                                        featureName = featureName,
                                        method = jsMessage.method,
                                        id = id,
                                        params = JSONObject().put("chats", JSONArray()),
                                    ),
                                )
                            }
                        }
                    }
                    "putChat" -> {
                        val chatId = jsMessage.params.optString("chatId")
                        val dataObj = jsMessage.params.optJSONObject("data") ?: return
                        if (chatId.isNotBlank()) {
                            logcat { "DuckAiNativeStorage: putChat chatId=$chatId" }
                            try {
                                chatsDao.upsert(DuckAiBridgeChatEntity(chatId = chatId, data = dataObj.toString()))
                            } catch (e: Exception) {
                                pixels.reportChatPutError()
                            }
                        }
                    }
                    "putChats", "migrateChats" -> {
                        val array = jsMessage.params.optJSONArray("chats") ?: return
                        try {
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
                        } catch (e: Exception) {
                            pixels.reportChatPutError()
                        }
                    }
                    "deleteChat" -> {
                        val chatId = jsMessage.params.optString("chatId")
                        if (chatId.isNotBlank()) {
                            logcat { "DuckAiNativeStorage: deleteChat chatId=$chatId" }
                            try {
                                chatsDao.delete(chatId)
                            } catch (e: Exception) {
                                pixels.reportChatDeleteError()
                            }
                        }
                    }
                    "deleteAllChats" -> {
                        logcat { "DuckAiNativeStorage: deleteAllChats" }
                        try {
                            chatsDao.deleteAll()
                        } catch (e: Exception) {
                            pixels.reportChatDeleteError()
                        }
                    }

                    // --- Migration ---
                    "isMigrationDone" -> {
                        val key = jsMessage.params.optString("key")
                        val done = try {
                            if (key.isNotBlank()) migrationPrefs.isMigrationDone(key) else false
                        } catch (e: Exception) {
                            pixels.reportMigrationError()
                            false
                        }
                        if (done) pixels.reportMigrationAlreadyDone()
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
                            try {
                                migrationPrefs.markMigrationDone(key)
                                pixels.reportMigrationDone(key)
                                pixels.reportMigrationStarted()
                            } catch (e: Exception) {
                                pixels.reportMigrationError()
                            }
                        } else {
                            pixels.reportMigrationDoneBlankKey()
                        }
                    }

                    // --- Files ---
                    "getFile" -> {
                        val uuid = jsMessage.params.optString("uuid")
                        try {
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
                        } catch (e: Exception) {
                            pixels.reportFileGetError()
                            jsMessage.id?.let { id ->
                                jsMessaging.onResponse(
                                    JsCallbackData(
                                        featureName = featureName,
                                        method = jsMessage.method,
                                        id = id,
                                        params = JSONObject().put("value", JSONObject.NULL),
                                    ),
                                )
                            }
                        }
                    }
                    "putFile" -> {
                        val uuid = jsMessage.params.optString("uuid")
                        if (isValidUuid(uuid)) {
                            val sizeBytes = jsMessage.params.toString().length
                            logcat { "DuckAiNativeStorage: putFile uuid=$uuid size=${sizeBytes}B" }
                            try {
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
                            } catch (e: Exception) {
                                pixels.reportFilePutError()
                            }
                        }
                    }
                    "deleteFile" -> {
                        val uuid = jsMessage.params.optString("uuid")
                        if (isValidUuid(uuid)) {
                            logcat { "DuckAiNativeStorage: deleteFile uuid=$uuid" }
                            try {
                                File(filesDir, uuid).delete()
                                fileMetaDao.delete(uuid)
                            } catch (e: Exception) {
                                pixels.reportFileDeleteError()
                            }
                        }
                    }
                    "deleteFiles" -> {
                        val chatId = jsMessage.params.optString("chatId")
                        if (chatId.isNotBlank()) {
                            logcat { "DuckAiNativeStorage: deleteFiles chatId=$chatId" }
                            try {
                                fileMetaDao.getByChatId(chatId).forEach { meta ->
                                    if (isValidUuid(meta.uuid)) File(filesDir, meta.uuid).delete()
                                }
                                fileMetaDao.deleteByChatId(chatId)
                            } catch (e: Exception) {
                                pixels.reportFileDeleteError()
                            }
                        }
                    }
                    "deleteAllFiles" -> {
                        logcat { "DuckAiNativeStorage: deleteAllFiles" }
                        try {
                            filesDir.listFiles()?.filter { isValidUuid(it.name) }?.forEach { it.delete() }
                            fileMetaDao.deleteAll()
                        } catch (e: Exception) {
                            pixels.reportFileDeleteError()
                        }
                    }
                    "listFiles" -> {
                        try {
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
                        } catch (e: Exception) {
                            pixels.reportFileListError()
                            jsMessage.id?.let { id ->
                                jsMessaging.onResponse(
                                    JsCallbackData(
                                        featureName = featureName,
                                        method = jsMessage.method,
                                        id = id,
                                        params = JSONObject().put("files", JSONArray()),
                                    ),
                                )
                            }
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
