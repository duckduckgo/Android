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

package com.duckduckgo.espresso

import android.webkit.WebView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.web.model.Atoms
import androidx.test.espresso.web.sugar.Web
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.mode.InAppNavigation
import com.duckduckgo.espresso.privacy.preparationsForPrivacyTest
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import logcat.logcat
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RequestBlocklistTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<BrowserActivity>(
        BrowserActivity.intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            launchSource = InAppNavigation,
            queryExtra = "https://privacy-test-pages.site/privacy-protections/request-blocklist/",
        ),
    )

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().resources.toList().forEach {
            IdlingRegistry.getInstance().unregister(it)
        }
    }

    @Test @InternalPrivacyTest
    fun whenRequestBlocklistIsEnabledRequestsAreHandledCorrectly() {
        preparationsForPrivacyTest()

        var webView: WebView? = null
        activityScenarioRule.scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }
        val browserWebView = webView!!

        // The requestBlocklist rules are NOT bundled with the app: they arrive via an async remote
        // privacy-config download and are then loaded into memory off the main thread, so on a
        // fresh/slow boot the page can issue its requests before blocking is live. There is no
        // observable signal for in-memory readiness, so we retry the real end condition: run the
        // page, and if requests that should be blocked still load, wait for the download to land,
        // reload, and try again — up to MAX_ATTEMPTS.
        //
        // This only compensates for config-download *timing*. A config that genuinely lacks rules
        // for the test domain, or the test site being unreachable, will (correctly) still fail
        // once the attempts are exhausted.
        var attempts = 0
        var overallStatus: String? = null
        var checks: List<RequestBlocklistResult> = emptyList()
        var failures: List<RequestBlocklistResult> = emptyList()

        while (true) {
            attempts++

            // window.results is only set when the page's finished() fires, i.e. the suite is
            // complete — the true completion signal. failOnTimeout = false so a slow run degrades
            // into another attempt here rather than crashing the whole test from the idling
            // resource's Handler.
            val resultsIdling = JsObjectIdlingResource(browserWebView, "window.results", failOnTimeout = false)
            IdlingRegistry.getInstance().register(resultsIdling)

            val results = Web.onWebView()
                .perform(Atoms.script(SCRIPT))
                .get()

            IdlingRegistry.getInstance().unregister(resultsIdling)

            val testJson: TestJson? = getTestJson(results.toJSONString())
            overallStatus = testJson?.value?.status
            checks = testJson?.value?.results.orEmpty()
            failures = checks.filter { it.actual != it.expected }
            logcat { "RequestBlocklistTest: attempt $attempts/$MAX_ATTEMPTS, status=$overallStatus, ${failures.size}/${checks.size} checks failing" }

            if (checks.isNotEmpty() && failures.isEmpty()) break
            if (attempts >= MAX_ATTEMPTS) break

            // Rules likely weren't loaded yet: give the background config download time to land,
            // then reload so the page re-issues its requests with blocking active.
            onView(isRoot()).perform(waitFor(DOWNLOAD_WAIT_MILLIS))
            reloadClearingResults(browserWebView)
        }

        assertTrue(
            "No request-blocklist checks were returned after $attempts attempt(s). Overall status: $overallStatus",
            checks.isNotEmpty(),
        )
        assertTrue(
            "Overall status was '$overallStatus' after $attempts attempt(s). ${failures.size} of ${checks.size} checks failed:\n" +
                failures.joinToString("\n") {
                    "  • \"${it.description}\": expected <${it.expected}> but was <${it.actual}>"
                },
            failures.isEmpty(),
        )
    }

    private fun reloadClearingResults(webView: WebView) {
        val cleared = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript("window.results = undefined;") { cleared.countDown() }
        }
        cleared.await(CLEAR_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.reload()
        }
    }

    private fun getTestJson(jsonString: String): TestJson? {
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val jsonAdapter: JsonAdapter<TestJson> = moshi.adapter(TestJson::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    companion object {
        const val SCRIPT = "return window.results;"

        private const val MAX_ATTEMPTS = 3

        // Time given to the background config download between attempts before reloading.
        private const val DOWNLOAD_WAIT_MILLIS = 30_000L

        // Best-effort bound for the synchronous window.results clear before a reload.
        private const val CLEAR_TIMEOUT_MILLIS = 5_000L
    }

    data class TestJson(
        val status: Int,
        val value: ResultsWrapper,
    )

    data class ResultsWrapper(
        val status: String,
        val results: List<RequestBlocklistResult>,
    )

    data class RequestBlocklistResult(
        val expected: String,
        val description: String,
        val status: String,
        val actual: String,
    )
}
