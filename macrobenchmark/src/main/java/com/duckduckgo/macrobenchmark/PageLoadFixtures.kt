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

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONObject

private const val CSS_COUNT = 20
private const val JS_COUNT = 20
private const val IMAGE_COUNT = 10
private const val CSS_TRACKER_COUNT = 8
private const val JS_TRACKER_COUNT = 8
private const val IMAGE_TRACKER_COUNT = 4
private const val TRACKER_HOST_COUNT = CSS_TRACKER_COUNT + JS_TRACKER_COUNT + IMAGE_TRACKER_COUNT
private const val CSS_THIRD_PARTY_COUNT = 10
private const val JS_THIRD_PARTY_COUNT = 10
private const val IMAGE_THIRD_PARTY_COUNT = 5
private const val CPM_MARKER =
    "<div id=\"gdpr-cookie-consent-bar\"><button id=\"cookie_action_reject\">Reject</button></div>"

private val ONE_PIXEL_PNG = Base64.decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
    Base64.DEFAULT,
)
private val ONE_PIXEL_GIF = Base64.decode("R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==", Base64.DEFAULT)

private val CSS_TRACKER_NAMES = listOf(
    "analytics", "gtm", "optimizely", "hotjar", "segment", "mixpanel", "amplitude", "criteo",
    "outbrain", "taboola", "quantserve", "chartbeat", "newrelic", "bugsnag", "sentry", "fullstory",
    "clarity", "adroll", "pardot", "marketo",
)

private val JS_TRACKER_NAMES = listOf(
    "analytics", "gpt", "fbevents", "gtag", "adsbygoogle", "doubleclick", "criteo-sync", "taboola-loader",
    "outbrain-widget", "hotjar-loader", "mixpanel-lib", "segment-analytics", "amplitude-lib", "quantcast",
    "chartbeat-loader", "newrelic-agent", "bugsnag-client", "sentry-bundle", "clarity-loader", "marketo-forms",
)

private val IMAGE_TRACKER_NAMES = listOf(
    "pixel", "fb-pixel", "ga-pixel", "doubleclick-pixel", "criteo-pixel", "taboola-pixel",
    "outbrain-pixel", "quantserve-pixel", "chartbeat-pixel", "adroll-pixel",
)

// Pinned rather than derived from raw/tds so the scenario is reproducible across TDS updates
// (verifyBlockingTrackerDomains below asserts each one is still default=block).
private val PINNED_TRACKER_DOMAINS = listOf(
    "quantserve.com", "criteo.com", "criteo.net", "taboola.com", "hotjar.com",
    "segment.io", "mixpanel.com", "amplitude.com", "newrelic.com", "nr-data.net",
    "adroll.com", "yieldmo.com", "adsrvr.org", "rubiconproject.com", "pubmatic.com",
    "openx.net", "demdex.net", "bluekai.com", "rlcdn.com", "fullstory.com",
)

internal interface PageLoadFixture {
    val name: String
    fun respond(request: RecordedRequest): MockResponse
}

private fun ordinaryIndexPage(cpmMarker: Boolean = false): String = buildString {
    append("<!DOCTYPE html><html><head>")
    repeat(CSS_COUNT) { append("<link rel=\"stylesheet\" href=\"/style-$it.css\">") }
    repeat(JS_COUNT) { append("<script src=\"/script-$it.js\"></script>") }
    append("</head><body>")
    if (cpmMarker) append(CPM_MARKER)
    append("<h1 id=\"title\">benchmark</h1>")
    repeat(IMAGE_COUNT) { append("<img src=\"/image-$it.png\">") }
    append("<script>document.getElementById('title').textContent = 'loaded';</script>")
    append("</body></html>")
}

private fun ordinaryResponse(path: String, indexPage: String): MockResponse = when {
    path == "/" -> htmlResponse(indexPage)
    path.startsWith("/style-") -> cssResponse()
    path.startsWith("/script-") -> jsResponse()
    path.startsWith("/image-") -> pngResponse()
    else -> MockResponse().setResponseCode(404)
}

internal object NoTrackersFixture : PageLoadFixture {
    override val name = "no-trackers"
    private val indexPage = ordinaryIndexPage()

    override fun respond(request: RecordedRequest): MockResponse = ordinaryResponse(request.path(), indexPage)
}

internal object FirstPartyTrackersFixture : PageLoadFixture {
    override val name = "first-party-trackers"
    private val indexPage = buildString {
        append("<!DOCTYPE html><html><head>")
        CSS_TRACKER_NAMES.forEach { append("<link rel=\"stylesheet\" href=\"/$it.css\">") }
        JS_TRACKER_NAMES.forEach { append("<script src=\"/$it.js\"></script>") }
        append("</head><body><h1 id=\"title\">benchmark</h1>")
        IMAGE_TRACKER_NAMES.forEach { append("<img src=\"/$it.gif\">") }
        append("<script>document.getElementById('title').textContent = 'loaded';</script>")
        append("</body></html>")
    }

    override fun respond(request: RecordedRequest): MockResponse = when (val path = request.path()) {
        "/" -> htmlResponse(indexPage)
        else -> when {
            path.endsWith(".css") -> cssResponse()
            path.endsWith(".js") -> jsResponse()
            path.endsWith(".gif") -> gifResponse()
            else -> MockResponse().setResponseCode(404)
        }
    }
}

internal object CpmFixture : PageLoadFixture {
    override val name = "cpm"
    private val indexPage = ordinaryIndexPage(cpmMarker = true)

    override fun respond(request: RecordedRequest): MockResponse = ordinaryResponse(request.path(), indexPage)
}

internal object ManyTrackersBlockedFixture : PageLoadFixture {
    override val name = "many-trackers-blocked"

    private val indexPage: String by lazy {
        val nextDomain = trackerDomainCursor()
        buildString {
            append("<!DOCTYPE html><html><head>")
            repeat(CSS_COUNT) { index ->
                val url = if (index < CSS_TRACKER_COUNT) "http://${nextDomain()}/style-$index.css" else "/style-$index.css"
                append("<link rel=\"stylesheet\" href=\"$url\">")
            }
            repeat(JS_COUNT) { index ->
                val url = if (index < JS_TRACKER_COUNT) "http://${nextDomain()}/script-$index.js" else "/script-$index.js"
                append("<script src=\"$url\"></script>")
            }
            append("</head><body><h1 id=\"title\">benchmark</h1>")
            repeat(IMAGE_COUNT) { index ->
                val url = if (index < IMAGE_TRACKER_COUNT) "http://${nextDomain()}/image-$index.png" else "/image-$index.png"
                append("<img src=\"$url\">")
            }
            append("<script>document.getElementById('title').textContent = 'loaded';</script>")
            append("</body></html>")
        }
    }

    override fun respond(request: RecordedRequest): MockResponse = ordinaryResponse(request.path(), indexPage)
}

internal object AllScenariosFixture : PageLoadFixture {
    override val name = "all"

    private val indexPage: String by lazy {
        val nextDomain = trackerDomainCursor()
        buildString {
            append("<!DOCTYPE html><html><head>")
            repeat(CSS_COUNT) { index ->
                val name = CSS_TRACKER_NAMES[index]
                val url = if (index < CSS_THIRD_PARTY_COUNT) "http://${nextDomain()}/$name-$index.css" else "/$name-$index.css"
                append("<link rel=\"stylesheet\" href=\"$url\">")
            }
            repeat(JS_COUNT) { index ->
                val name = JS_TRACKER_NAMES[index]
                val url = if (index < JS_THIRD_PARTY_COUNT) "http://${nextDomain()}/$name-$index.js" else "/$name-$index.js"
                append("<script src=\"$url\"></script>")
            }
            append("</head><body>")
            append(CPM_MARKER)
            append("<h1 id=\"title\">benchmark</h1>")
            repeat(IMAGE_COUNT) { index ->
                val name = IMAGE_TRACKER_NAMES[index]
                val url = if (index < IMAGE_THIRD_PARTY_COUNT) "http://${nextDomain()}/$name-$index.gif" else "/$name-$index.gif"
                append("<img src=\"$url\">")
            }
            append("<script>document.getElementById('title').textContent = 'loaded';</script>")
            append("</body></html>")
        }
    }

    override fun respond(request: RecordedRequest): MockResponse = when (val path = request.path()) {
        "/" -> htmlResponse(indexPage)
        else -> when {
            path.endsWith(".css") -> cssResponse()
            path.endsWith(".js") -> jsResponse()
            path.endsWith(".gif") -> gifResponse()
            else -> MockResponse().setResponseCode(404)
        }
    }
}

private val trackerDomains: List<String> by lazy {
    check(PINNED_TRACKER_DOMAINS.size >= TRACKER_HOST_COUNT) {
        "Need at least $TRACKER_HOST_COUNT pinned tracker domains, found ${PINNED_TRACKER_DOMAINS.size}"
    }
    verifyBlockingTrackerDomains(PINNED_TRACKER_DOMAINS)
}

private fun trackerDomainCursor(): () -> String {
    var index = 0
    return { trackerDomains[index++ % trackerDomains.size] }
}

private fun verifyBlockingTrackerDomains(domains: List<String>): List<String> {
    val packageManager = InstrumentationRegistry.getInstrumentation().context.packageManager
    val resources = packageManager.getResourcesForApplication(TARGET_PACKAGE)
    val resourceId = resources.getIdentifier("tds", "raw", TARGET_PACKAGE)
    check(resourceId != 0) { "Could not find the target app's raw/tds resource" }
    val json = resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
    val trackers = JSONObject(json).getJSONObject("trackers")
    domains.forEach { domain ->
        val entry = trackers.optJSONObject(domain)
        checkNotNull(entry) { "Pinned tracker domain '$domain' is no longer present in raw/tds; update PINNED_TRACKER_DOMAINS" }
        val rules = entry.optJSONArray("rules")
        val isBlockDefault = entry.optString("default") == "block" && (rules == null || rules.length() == 0)
        check(isBlockDefault) { "Pinned tracker domain '$domain' is no longer default=block in raw/tds; update PINNED_TRACKER_DOMAINS" }
    }
    return domains
}

private fun RecordedRequest.path(): String = requestUrl?.encodedPath.orEmpty()

private fun htmlResponse(body: String) = MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(body)
private fun cssResponse() = MockResponse().setHeader("Content-Type", "text/css").setBody("body{margin:0}")
private fun jsResponse() = MockResponse().setHeader("Content-Type", "application/javascript").setBody("void 0;")
private fun pngResponse() = MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(ONE_PIXEL_PNG))
private fun gifResponse() = MockResponse().setHeader("Content-Type", "image/gif").setBody(Buffer().write(ONE_PIXEL_GIF))
