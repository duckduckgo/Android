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
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.web.model.Atoms
import androidx.test.espresso.web.sugar.Web
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.espresso.privacy.preparationsForPrivacyTest
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RequestBlocklistTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<BrowserActivity>(
        BrowserActivity.intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            queryExtra = "https://privacy-test-pages.site/privacy-protections/request-blocklist/",
        ),
    )

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().resources.toList().forEach { resource ->
            IdlingRegistry.getInstance().unregister(resource)
        }
    }

    @Test
    fun whenRequestBlocklistIsEnabledRequestsAreHandledCorrectly() {
        preparationsForPrivacyTest()

        var webView: WebView? = null
        activityScenarioRule.scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }

        // Wait for privacy config to download and populate the request blocklist
        Espresso.onView(ViewMatchers.isRoot()).perform(waitFor(10000))

        // Reload the page so requests are intercepted with the now-loaded blocklist
        activityScenarioRule.scenario.onActivity {
            webView?.reload()
        }

        // Small delay to ensure reload has started and old JS context is cleared
        Espresso.onView(ViewMatchers.isRoot()).perform(waitFor(1000))

        // Now register — window.results won't exist until the new page's finished() fires
        val idlingResourceForResults = JsObjectIdlingResource(webView!!, "window.results")
        IdlingRegistry.getInstance().register(idlingResourceForResults)

        val results = Web.onWebView()
            .perform(Atoms.script(SCRIPT))
            .get()

        val testJson: TestJson? = getTestJson(results.toJSONString())
        assertEquals(
            "Overall status should be success but was: ${testJson?.value?.status}",
            SUCCESS,
            testJson?.value?.status,
        )
        testJson?.value?.results?.forEach { result ->
            assertEquals(
                "\"${result.description}\" expected ${result.expected} but was ${result.actual}",
                result.expected,
                result.actual,
            )
        }
    }

    private fun getTestJson(jsonString: String): TestJson? {
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val jsonAdapter: JsonAdapter<TestJson> = moshi.adapter(TestJson::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    companion object {
        const val SCRIPT = "return window.results;"
        const val SUCCESS = "success"
    }

    data class TestJson(val status: Int, val value: ResultsWrapper)
    data class ResultsWrapper(val status: String, val results: List<RequestBlocklistResult>)
    data class RequestBlocklistResult(
        val expected: String,
        val description: String,
        val status: String,
        val actual: String,
    )
}
