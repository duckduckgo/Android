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
import com.duckduckgo.duckchat.localserver.impl.DuckAiImagesDir
import com.duckduckgo.duckchat.localserver.impl.DuckAiRequestHandler
import com.squareup.anvil.annotations.ContributesMultibinding
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONArray
import org.json.JSONObject
import dagger.Lazy
import java.io.File
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ImagesHandler @Inject constructor(
    @DuckAiImagesDir private val imagesDirLazy: Lazy<File>,
) : DuckAiRequestHandler {

    private val imagesDir: File get() = imagesDirLazy.get()

    override val pathPrefix = "/images"

    override fun handle(method: Method, uri: String, body: String?): Response {
        return if (uri == "/images") {
            handleDeleteAll(method)
        } else {
            val uuid = uri.removePrefix("/images/").takeIf { it.isNotBlank() }
            handleSingle(method, uuid, body)
        }
    }

    private fun handleDeleteAll(method: Method): Response {
        return when (method) {
            Method.GET -> {
                val array = JSONArray()
                imagesDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
                    runCatching { JSONObject(file.readText()) }.getOrNull()?.let { json ->
                        array.put(JSONObject().apply {
                            put("uuid", json.optString("uuid", file.name))
                            put("chatId", json.optString("chatId", ""))
                            put("dataSize", json.optString("data", "").length)
                        })
                    }
                }
                NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", array.toString())
            }
            Method.DELETE -> {
                imagesDir.listFiles()?.forEach { it.delete() }
                NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
            }
            else -> NanoHTTPD.newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "Method not allowed")
        }
    }

    private fun handleSingle(method: Method, uuid: String?, body: String?): Response {
        if (uuid == null || !isValidUuid(uuid)) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Invalid uuid")
        }
        return when (method) {
            Method.GET -> handleGet(uuid)
            Method.PUT -> handlePut(uuid, body)
            Method.DELETE -> handleDelete(uuid)
            else -> NanoHTTPD.newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "Method not allowed")
        }
    }

    private fun handleGet(uuid: String): Response {
        val file = File(imagesDir, uuid)
        if (!file.exists()) {
            return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not found")
        }
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", file.readText())
    }

    private fun handlePut(uuid: String, body: String?): Response {
        if (body.isNullOrBlank()) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Body required")
        }
        try {
            JSONObject(body)
        } catch (e: Exception) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Body must be a JSON object")
        }
        imagesDir.mkdirs()
        File(imagesDir, uuid).writeText(body)
        return NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
    }

    private fun handleDelete(uuid: String): Response {
        File(imagesDir, uuid).delete()
        return NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
    }

    private fun isValidUuid(uuid: String): Boolean {
        if (uuid.contains('/') || uuid.contains('\\') || uuid.contains("..")) return false
        val candidate = File(imagesDir, uuid).canonicalFile
        return candidate.parentFile?.canonicalFile == imagesDir.canonicalFile
    }
}
