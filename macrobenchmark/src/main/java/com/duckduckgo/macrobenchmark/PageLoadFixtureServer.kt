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

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.net.InetAddress
import java.util.concurrent.TimeUnit

private const val FAVICON_PATH = "/favicon.ico"
private const val TRACE_SENTINEL_PATH = "/trace-sentinel"

/**
 * Fixed delay on every tracker-host response, so protections off models "the network exists and answers" without jitter.
 * Changing it is a re-baseline.
 */
private const val TRACKER_RESPONSE_DELAY_MS = 30L

/**
 * Serves a page-load fixture from the device itself via a loopback MockWebServer. In mock environment the app's WebView
 * proxies every non-loopback request here, so it also answers for tracker hosts and records anything else it is asked for.
 */
internal class PageLoadFixtureServer(
    fixture: PageLoadFixture,
    scenario: String,
) {
    private val server = MockWebServer()
    private val trackerHosts = trackerDomains.toSet()
    private val ledger = RequestLedger(scenario, trackerHosts)
    private val dispatcher = FixtureDispatcher(fixture, ledger, trackerHosts)

    private val origin: String get() = "http://$HOST:${server.port}"
    val baseUrl: String get() = "$origin/"
    val traceSentinelUrl: String get() = "$origin$TRACE_SENTINEL_PATH"
    val port: Int get() = server.port

    fun start() {
        server.dispatcher = dispatcher
        server.start(InetAddress.getByName(HOST), 0)
    }

    /**
     * Drops everything recorded so far, so the report covers only the measured navigations.
     */
    fun startCounting() = ledger.reset()

    /**
     * Stops the server first so late requests are in the report.
     */
    fun shutdown() {
        server.shutdown()
        ledger.logReport()
    }

    companion object {
        const val HOST = "127.0.0.1"
    }
}

private class FixtureDispatcher(
    private val fixture: PageLoadFixture,
    private val ledger: RequestLedger,
    private val trackerHosts: Set<String>,
) : Dispatcher() {

    override fun dispatch(request: RecordedRequest): MockResponse {
        val parts = request.requestLine.split(" ")
        if (parts.firstOrNull() == "CONNECT") {
            ledger.recordConnect(parts.getOrElse(1) { "?" })
            return MockResponse().setResponseCode(BAD_GATEWAY)
        }
        // Proxied requests use the absolute form; loopback ones carry only a path and the Host header.
        val target = parts.getOrElse(1) { "" }
        val url = (if (target.startsWith("/")) "http://${request.getHeader("Host")}$target" else target).toHttpUrlOrNull()
        val host = url?.host
        return when {
            host == null -> unknown("?")
            host == PageLoadFixtureServer.HOST -> respondFirstParty(url.encodedPath)
            host in trackerHosts -> {
                ledger.recordServed(host)
                trackerResponse(url.encodedPath)
            }
            else -> unknown(host)
        }
    }

    private fun unknown(host: String): MockResponse {
        ledger.recordUnknown(host)
        return MockResponse().setResponseCode(BAD_GATEWAY)
    }

    private fun respondFirstParty(path: String): MockResponse = when (path) {
        FAVICON_PATH -> MockResponse().setResponseCode(204).setHeader("Cache-Control", "no-store")
        TRACE_SENTINEL_PATH -> MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody("<!doctype html>")
        else -> {
            ledger.recordServed(PageLoadFixtureServer.HOST)
            fixture.respond(path).setHeader("Cache-Control", "no-store")
        }
    }

    private fun trackerResponse(path: String): MockResponse {
        val response = when {
            path.endsWith(".css") -> MockResponse().setHeader("Content-Type", "text/css")
            path.endsWith(".js") -> MockResponse().setHeader("Content-Type", "application/javascript")
            path.endsWith(".png") -> pngResponse()
            path.endsWith(".gif") -> gifResponse()
            else -> MockResponse().setResponseCode(404)
        }
        return response.setHeader("Cache-Control", "no-store").setHeadersDelay(TRACKER_RESPONSE_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private companion object {
        const val BAD_GATEWAY = 502
    }
}
