/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.espresso.adclick

import android.webkit.WebView
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.web.assertion.WebViewAssertions
import androidx.test.espresso.web.model.Atoms
import androidx.test.espresso.web.sugar.Web
import androidx.test.espresso.web.webdriver.DriverAtoms
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.espresso.PrivacyTest
import com.duckduckgo.espresso.WebViewIdlingResource
import com.duckduckgo.espresso.waitForView
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.hamcrest.CoreMatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class SingleSiteSingleTabSessionTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<BrowserActivity>(
        BrowserActivity.intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            queryExtra = "https://www.search-company.site/"
        )
    )

    @Test
    @PrivacyTest
    fun whenUserClicksOnAd1ThenConvertIsLoadedAndTrackIsBlocked() {
        val waitTime = 16000L
        IdlingPolicies.setMasterPolicyTimeout(waitTime * 10, TimeUnit.MILLISECONDS)
        IdlingPolicies.setIdlingResourceTimeout(waitTime * 10, TimeUnit.MILLISECONDS)

        var webView: WebView? = null

        Espresso.onView(ViewMatchers.isRoot()).perform(waitForView(ViewMatchers.withId(R.id.pageLoadingIndicator)))

        activityScenarioRule.scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }

        val idlingResourceForDisableProtections = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForDisableProtections)

        Web.onWebView()
            .withElement(DriverAtoms.findElement(Locator.ID, "ad-id-1"))
            .check(WebViewAssertions.webMatches(DriverAtoms.getText(), CoreMatchers.containsString("[Ad 1] SERP Ad (heuristic)")))
            .perform(DriverAtoms.webClick())

        val idlingResourceForScript = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForScript)

        val results = Web.onWebView()
            .perform(Atoms.script(SCRIPT))
            .get()

        val testJson: TestJson? = getTestJson(results.toJSONString())
        assertEquals(expected.size, testJson?.value?.size)
        testJson?.value?.map {
            assertEquals(expected[it.url], it.status)
        }
        IdlingRegistry.getInstance().unregister(idlingResourceForDisableProtections, idlingResourceForScript)
    }

    private fun getTestJson(jsonString: String): TestJson? {
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val jsonAdapter: JsonAdapter<TestJson> = moshi.adapter(TestJson::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    companion object {
        const val SCRIPT = "return window.resources;"
        val expected = mapOf(
            "https://convert.ad-company.site/convert.js?ad=1" to "loaded",
            "https://www.ad-company.site/track.js?ad=1" to "blocked",
            "https://convert.ad-company.site/ping.gif" to "loaded",
            "https://www.ad-company.site/ping.gif" to "parent blocked"
        )
    }

    data class TestJson(val status: Int, val value: List<SingleSiteSingleTabSessionTest>)
    data class SingleSiteSingleTabSessionTest(val status: String, val url: String?)
}
