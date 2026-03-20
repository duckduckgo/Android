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
import com.duckduckgo.duckchat.localserver.impl.store.DuckAiMigrationDao
import com.squareup.anvil.annotations.ContributesMultibinding
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class MigrationHandler @Inject constructor(
    private val dao: DuckAiMigrationDao,
) : DuckAiRequestHandler {

    override val pathPrefix = "/migration"

    override fun handle(method: Method, uri: String, body: String?): Response {
        return when (method) {
            Method.GET -> {
                val json = JSONObject().put("done", dao.isDone())
                NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", json.toString())
            }
            Method.POST -> {
                dao.markDone()
                NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
            }
            Method.DELETE -> {
                dao.reset()
                NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
            }
            else -> NanoHTTPD.newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "Method not allowed")
        }
    }
}
