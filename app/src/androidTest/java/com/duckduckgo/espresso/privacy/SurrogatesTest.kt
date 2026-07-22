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
import com.duckduckgo.app.browser.clickMenuItem
import com.duckduckgo.app.browser.mode.InAppNavigation
import com.duckduckgo.espresso.*
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SurrogatesTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<BrowserActivity>(
        BrowserActivity.intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            launchSource = InAppNavigation,
            queryExtra = "https://privacy-test-pages.site/privacy-protections/surrogates/",
        ),
    )

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().resources.toList().forEach { resource ->
            IdlingRegistry.getInstance().unregister(resource)
        }
    }

    @Test @PrivacyTest
    fun whenProtectionsAreEnabledSurrogatesAreLoaded() {
        preparationsForPrivacyTest()

        var webView: WebView? = null
        activityScenarioRule.scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }

        val idlingResourceForDisableProtections = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForDisableProtections)

        val results = onWebView()
            .perform(script(SCRIPT))
            .get()

        val testJson: TestJson? = getTestJson(results.toJSONString())

        testJson?.value?.forEach {
            if (compatibleIds.contains(it.id)) {
                assertTrue("Loaded for ${it.id} should be loaded and is ${it.loaded}", it.loaded)
            }
        }
    }

    @Test @PrivacyTest
    fun whenProtectionsAreDisabledSurrogatesAreNotLoaded() {
        preparationsForPrivacyTest()

        var webView: WebView? = null
        activityScenarioRule.scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }

        val idlingResourceForDisableProtections = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForDisableProtections)

        onView(allOf(withId(R.id.browserMenu), isClickable())).perform(ViewActions.click())
        // Protections may already be disabled for privacy-test-pages.site if a previous test in
        // the same run left that state behind — the user allowlist persists across tests and is
        // only cleared between orchestrated (CI) runs, not local connectedAndroidTest runs. When
        // already disabled, the menu shows "Enable Privacy Protection" instead, and we're already
        // in the state under test, so skip the disable click rather than failing to find it.
        runCatching { clickMenuItem(withText("Disable Privacy Protection")) }

        // Dismiss the privacy protection toggle check screen (if we disabled) or the menu (if it
        // was already disabled).
        onView(isRoot()).perform(ViewActions.pressBack())

        val idlingResourceForScript: IdlingResource = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForScript)

        val results = onWebView()
            .perform(script(SCRIPT))
            .get()

        val testJson: TestJson? = getTestJson(results.toJSONString())

        testJson?.value?.forEach {
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
