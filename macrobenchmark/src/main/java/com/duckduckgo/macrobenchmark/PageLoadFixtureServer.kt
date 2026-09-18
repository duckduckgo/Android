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

package com.duckduckgo.macrobenchmark

import android.util.Log
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "PageLoadFixtureServer"
private const val FAVICON_PATH = "/favicon.ico"
private const val TRACE_SENTINEL_PATH = "/trace-sentinel"

/** Serves page-load fixtures from the device itself via a loopback MockWebServer. */
internal class PageLoadFixtureServer(fixture: PageLoadFixture) {
    private val server = MockWebServer()
    private val dispatcher = FixtureDispatcher(fixture)

    private val origin: String get() = "http://$HOST:${server.port}"
    val baseUrl: String get() = "$origin/"
    val traceSentinelUrl: String get() = "$origin$TRACE_SENTINEL_PATH"

    fun start() {
        server.dispatcher = dispatcher
        server.start(InetAddress.getByName(HOST), 0)
    }

    fun shutdown() {
        dispatcher.logSummary()
        server.shutdown()
    }

    companion object {
        const val HOST = "127.0.0.1"
    }
}

private class FixtureDispatcher(private val fixture: PageLoadFixture) : Dispatcher() {
    private val requestCount = AtomicInteger(0)
    private val pathCounts = ConcurrentHashMap<String, Int>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.requestUrl?.encodedPath.orEmpty()
        pathCounts.merge(path, 1, Int::plus)
        if (path == FAVICON_PATH) {
            return MockResponse().setResponseCode(204).setHeader("Cache-Control", "no-store")
        }
        if (path == TRACE_SENTINEL_PATH) {
            return MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody("<!doctype html>")
        }
        requestCount.incrementAndGet()
        return fixture.respond(request).setHeader("Cache-Control", "no-store")
    }

    fun logSummary() {
        Log.i(TAG, "fixture=${fixture.name} served requestCount=${requestCount.get()} pathCounts=$pathCounts")
    }
}
