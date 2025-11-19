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
import androidx.test.core.app.*
import androidx.test.espresso.*
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.web.model.Atoms.script
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.espresso.*
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SurrogatesTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<BrowserActivity>()

    @Test @PrivacyTest
    fun whenProtectionsAreEnabledSurrogatesAreLoaded() {
        preparationsForPrivacyTest()

        var webView: WebView? = null

        val scenario = ActivityScenario.launch<BrowserActivity>(
            BrowserActivity.intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                "https://privacy-test-pages.site/privacy-protections/surrogates/",
            ),
        )
        scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }

        val idlingResourceForDisableProtections = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForDisableProtections)

        val results = onWebView()
            .perform(script(SCRIPT))
            .get()

        val testJson: TestJson? = getTestJson(results.toJSONString())

        testJson?.value?.map {
            if (compatibleIds.contains(it.id)) {
                assertTrue("Loaded for ${it.id} should be loaded and is ${it.loaded}", it.loaded)
            }
        }
    }

    @Test @PrivacyTest
    fun whenProtectionsAreDisabledSurrogatesAreNotLoaded() {
        preparationsForPrivacyTest()

        var webView: WebView? = null

        val scenario = ActivityScenario.launch<BrowserActivity>(
            BrowserActivity.intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                "https://privacy-test-pages.site/privacy-protections/surrogates/",
            ),
        )
        scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }

        val idlingResourceForDisableProtections = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForDisableProtections)

        onView(allOf(withId(R.id.browserMenu), isClickable())).perform(ViewActions.click())
        onView(isRoot()).perform(waitForView(withId(R.id.privacyProtectionMenuItem)))
        onView(withId(R.id.privacyProtectionMenuItem)).perform(ViewActions.click())

        // handle the privacy protection toggle check screen showing
        onView(isRoot()).perform(ViewActions.pressBack())

        val idlingResourceForScript: IdlingResource = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForScript)

        val results = onWebView()
            .perform(script(SCRIPT))
            .get()

        val testJson: TestJson? = getTestJson(results.toJSONString())

        testJson?.value?.map {
            if (compatibleIds.contains(it.id)) {
                assertFalse("Loaded for ${it.id} should not be loaded and is ${it.loaded}", it.loaded)
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
        val compatibleIds = listOf("main-frame", "sub-frame")
    }

    data class TestJson(val status: Int, val value: List<SurrogatesTest>)
    data class SurrogatesTest(val id: String, val loaded: Boolean)
}
