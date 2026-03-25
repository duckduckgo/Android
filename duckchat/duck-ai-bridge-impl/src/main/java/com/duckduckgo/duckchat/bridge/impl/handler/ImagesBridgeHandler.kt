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
import com.duckduckgo.duckchat.bridge.impl.DuckAiBridgeImagesDir
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exposes image file storage to duck.ai JS as `window.ImagesBridge`.
 *
 * File I/O is dispatched to [dispatchers.io()] — [addWebMessageListener] fires on the main thread.
 * Business logic is in `internal` functions so tests can call them directly.
 */
@ContributesMultibinding(AppScope::class)
class ImagesBridgeHandler @Inject constructor(
    @DuckAiBridgeImagesDir private val imagesDirLazy: Lazy<File>,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : DuckAiBridgeHandler {

    private val imagesDir: File get() = imagesDirLazy.get()

    override val bridgeName = "ImagesBridge"

    override fun onMessage(message: String, replyProxy: JavaScriptReplyProxy) {
        val json = try { JSONObject(message) } catch (e: Exception) { return }
        appScope.launch(dispatchers.io()) {
            when (json.optString("action")) {
                "getImage" -> {
                    val uuid = json.optString("uuid")
                    val value = handleGet(uuid)
                    val result = JSONObject().apply {
                        put("action", "getImage")
                        put("value", value ?: JSONObject.NULL)
                    }
                    replyProxy.postMessage(result.toString())
                }
                "putImage" -> json.optJSONObject("data")?.toString()?.let { handlePut(json.optString("uuid"), it) }
                "deleteImage" -> handleDelete(json.optString("uuid"))
                "deleteAllImages" -> handleDeleteAll()
                "listImages" -> {
                    val result = JSONObject().apply {
                        put("action", "listImages")
                        put("value", handleList())
                    }
                    replyProxy.postMessage(result.toString())
                }
            }
        }
    }

    internal fun handleGet(uuid: String): String? {
        if (!isValidUuid(uuid)) return null
        val file = File(imagesDir, uuid)
        return if (file.exists()) file.readText() else null
    }

    internal fun handlePut(uuid: String, json: String) {
        if (!isValidUuid(uuid)) return
        try { JSONObject(json) } catch (e: Exception) { return }
        imagesDir.mkdirs()
        File(imagesDir, uuid).writeText(json)
    }

    internal fun handleDelete(uuid: String) {
        if (!isValidUuid(uuid)) return
        File(imagesDir, uuid).delete()
    }

    internal fun handleDeleteAll() {
        imagesDir.listFiles()?.forEach { it.delete() }
    }

    internal fun handleList(): String {
        val array = JSONArray()
        imagesDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            runCatching { JSONObject(file.readText()) }.getOrNull()?.let { json ->
                array.put(
                    JSONObject().apply {
                        put("uuid", json.optString("uuid", file.name))
                        put("chatId", json.optString("chatId", ""))
                        put("dataSize", json.optString("data", "").length)
                    },
                )
            }
        }
        return array.toString()
    }

    private fun isValidUuid(uuid: String): Boolean {
        if (uuid.contains('/') || uuid.contains('\\') || uuid.contains("..")) return false
        val candidate = File(imagesDir, uuid).canonicalFile
        return candidate.parentFile?.canonicalFile == imagesDir.canonicalFile
    }
}
