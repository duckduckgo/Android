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

package com.duckduckgo.duckchat.localserver.impl

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.localserver.api.DuckAiLocalServer
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.IOException
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DuckAiLocalServer::class)
class RealDuckAiLocalServer @Inject constructor(
    private val originValidator: OriginValidator,
    private val requestHandlers: DaggerSet<DuckAiRequestHandler>,
    appBuildConfig: AppBuildConfig,
) : NanoHTTPD("127.0.0.1", if (appBuildConfig.isDebug) DEV_SERVER_PORT else 0), DuckAiLocalServer {

    override val port: Int
        get() = listeningPort  // NanoHTTPD provides this after start()

    override fun start() {
        try {
            start(SOCKET_READ_TIMEOUT, false /* not a daemon — lives with the app */)
            logcat { "DuckAiLocalServer started on port $port" }
        } catch (e: IOException) {
            logcat(LogPriority.ERROR) { "DuckAiLocalServer failed to bind: ${e.message}" }
        }
    }

    override fun stop() {
        super.stop()
        logcat { "DuckAiLocalServer stopped" }
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            serveInternal(session)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR) { "DuckAiLocalServer: unhandled exception: ${e.javaClass.name}: ${e.message}\n${e.stackTraceToString()}" }
            newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal error")
        }
    }

    private fun serveInternal(session: IHTTPSession): Response {
        if (!originValidator.isAllowed(session.headers)) {
            return newFixedLengthResponse(Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
        }

        val origin = session.headers["origin"]!! // safe: isAllowed guarantees present and non-blank

        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Status.NO_CONTENT, MIME_PLAINTEXT, "")
                .withCors(origin)
                .apply { addHeader("Access-Control-Max-Age", "86400") }
        }

        val uri = session.uri
        logcat { "DuckAiLocalServer: ${session.method} $uri handlers=${requestHandlers.size} prefixes=${requestHandlers.map { it.pathPrefix }}" }
        val handler = requestHandlers.firstOrNull { uri.startsWith(it.pathPrefix) }
            ?: return newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "Not found").withCors(origin)

        val body = if (session.method == Method.PUT) readBody(session) else null
        if (body == null && session.method == Method.PUT) {
            return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Body too large or incomplete").withCors(origin)
        }

        return handler.handle(session.method, uri, body).withCors(origin)
    }

    private fun Response.withCors(origin: String): Response = apply {
        addHeader("Access-Control-Allow-Origin", origin)
        addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        addHeader("Access-Control-Allow-Headers", "Content-Type, Origin")
        addHeader("Connection", "close")
    }

    private fun readBody(session: IHTTPSession): String? {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength > MAX_BODY_BYTES) {
            return null
        }
        val buf = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = session.inputStream.read(buf, offset, contentLength - offset)
            if (read == -1) break
            offset += read
        }
        // Reject truncated body — do not store partial data
        if (offset < contentLength) {
            return null
        }
        return buf.toString(Charsets.UTF_8)
    }

    companion object {
        private const val DEV_SERVER_PORT = 8765
        private const val MAX_BODY_BYTES = 10 * 1024 * 1024  // 10 MB — images encoded as base64 can reach ~3 MB
    }
}
