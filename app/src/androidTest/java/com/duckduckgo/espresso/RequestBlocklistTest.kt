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
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withId
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
            queryExtra = "https://privacy-test-pages.site/privacy-protections",
        ),
    )

    private val registeredResources = mutableListOf<IdlingResource>()

    @After
    fun tearDown() {
        registeredResources.forEach { IdlingRegistry.getInstance().unregister(it) }
    }

    @Test @InternalPrivacyTest
    fun whenRequestBlocklistIsEnabledRequestsAreHandledCorrectly() {
        preparationsForPrivacyTest()

        var webView: WebView? = null
        activityScenarioRule.scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }

        WebViewIdlingResource(webView!!).track()

        onView(withId(R.id.omnibarTextInput)).perform(
            clearText(),
            typeText("https://privacy-test-pages.site/privacy-protections/request-blocklist/"),
            pressImeActionButton(),
        )

        WebViewIdlingResource(webView!!).track()

        // Now register — window.results won't exist until the new page's finished() fires
        JsObjectIdlingResource(webView!!, "window.results").track()

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

    private fun IdlingResource.track() = apply {
        registeredResources += this
        IdlingRegistry.getInstance().register(this)
    }

    companion object {
        const val SCRIPT = "return window.results;"
        const val SUCCESS = "success"
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
