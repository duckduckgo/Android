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

package com.duckduckgo.espresso.privacy

import android.webkit.WebView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import androidx.test.espresso.web.model.Atoms.script
import com.duckduckgo.espresso.PrivacyTest
import com.duckduckgo.espresso.WebViewIdlingResource
import com.duckduckgo.espresso.waitForView
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class RequestBlockingTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<BrowserActivity>(
        BrowserActivity.intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "https://privacy-test-pages.glitch.me/privacy-protections/request-blocking/?run"
        )
    )

    @Test @PrivacyTest
    fun whenProtectionsAreEnabledRequestBlockedCorrectly() {
        onView(isRoot()).perform(waitForView(withId(R.id.pageLoadingIndicator)))

        val results = onWebView()
            .perform(script(SCRIPT))
            .get()

        val testJson: TestJson? = getTestJson(results.toJSONString())
        testJson?.value?.map {
            if (!enabledIgnoreIds.contains(it.id)) {
                assertTrue("Status for ${it.id} should be not loaded or failed and is ${it.status}", nonLoadedValues.contains(it.status))
            }
        }
    }

    @Test @PrivacyTest
    fun whenProtectionsAreDisabledRequestAreNotBlocked() {
        val waitTime = 16000L
        IdlingPolicies.setMasterPolicyTimeout(waitTime * 10, TimeUnit.MILLISECONDS)
        IdlingPolicies.setIdlingResourceTimeout(waitTime * 10, TimeUnit.MILLISECONDS)

        var webView: WebView? = null

        onView(isRoot()).perform(waitForView(withId(R.id.browserMenu)))

        activityScenarioRule.scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }

        val idlingResourceForDisableProtections = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForDisableProtections)

        onView(withId(R.id.browserMenu)).perform(click())
        onView(isRoot()).perform(waitForView(withId(R.id.privacyProtectionMenuItem)))
        onView(withId(R.id.privacyProtectionMenuItem)).perform(click())

        val idlingResourceForScript: IdlingResource = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForScript)

        val results = onWebView()
            .perform(script(SCRIPT))
            .get()

        val testJson: TestJson? = getTestJson(results.toJSONString())
        testJson?.value?.map {
            if (!disabledIgnoreIds.contains(it.id)) {
                assertEquals("Status for ${it.id} should be loaded and is ${it.status}", it.status, LOADED)
            }
        }

        IdlingRegistry.getInstance().unregister(idlingResourceForDisableProtections, idlingResourceForScript)
    }

    private fun getTestJson(jsonString: String): TestJson? {
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val jsonAdapter: JsonAdapter<TestJson> = moshi.adapter(TestJson::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    companion object {
        const val SCRIPT = "return results.results;"
        const val LOADED = "loaded"
        val nonLoadedValues = listOf("not loaded", "failed")
        val enabledIgnoreIds = listOf("font")
        val disabledIgnoreIds = listOf("websocket")
    }

    data class TestJson(val status: Int, val value: List<RequestBlockingTest>)
    data class RequestBlockingTest(val id: String, val category: String, val status: String)
}
