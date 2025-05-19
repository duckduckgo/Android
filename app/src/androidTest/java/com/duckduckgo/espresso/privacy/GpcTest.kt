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
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.espresso.web.model.Atoms.script
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.getText
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.Locator.ID
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.espresso.JsObjectIdlingResource
import com.duckduckgo.espresso.PrivacyTest
import com.duckduckgo.espresso.WebViewIdlingResource
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GpcTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<BrowserActivity>(
        BrowserActivity.intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            queryExtra = "https://privacy-test-pages.site/privacy-protections",
        ),
    )

    private val registeredResources = mutableListOf<IdlingResource>()

    @Test @PrivacyTest
    fun whenProtectionsAreEnableGpcSetCorrectly() {
        preparationsForPrivacyTest()

        var webView: WebView? = null

        activityScenarioRule.scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }

        WebViewIdlingResource(webView!!).track()
        onView(withId(R.id.omnibarTextInput)).perform(
            typeText("https://privacy-test-pages.site/privacy-protections/gpc/"),
            pressImeActionButton(),
        )
        WebViewIdlingResource(webView!!).track()

        // asserts we have injected css by querying the duckduckgo object
        JsObjectIdlingResource(webView!!, "window.navigator.duckduckgo").track()

        onWebView()
            .withElement(findElement(ID, "start"))
            .check(webMatches(getText(), containsString("Start test")))
            .perform(webClick())

        WebViewIdlingResource(webView!!).track()

        val results = onWebView()
            .perform(script(SCRIPT))
            .get()

        val testJson: TestJson? = getTestJson(results.toJSONString())
        testJson?.value?.map {
            if (compatibleIds.contains(it.id)) {
                assertTrue("Value ${it.id} should be true", it.value.toString() == "true")
            }
        }
    }

    @After
    fun unregisterIdlingResources() {
        registeredResources.forEach { IdlingRegistry.getInstance().unregister(it) }
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
        const val SCRIPT = "return results.results;"
        val compatibleIds = listOf("top frame JS API")
    }

    data class TestJson(val status: Int, val value: List<GpcTest>)
    data class GpcTest(val id: String, val value: Any)
}
