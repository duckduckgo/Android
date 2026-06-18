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

import android.view.View
import android.webkit.WebView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
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
import org.hamcrest.Matcher
import org.hamcrest.Matchers.instanceOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RequestBlocklistTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<BrowserActivity>(
        BrowserActivity.intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            launchSource = InAppNavigation,
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

        val grabber = WebViewGrabber()
        onView(withId(R.id.browserWebView)).perform(grabber)
        val webView = grabber.webView ?: error("browserWebView not present after waitForView")

        WebViewIdlingResource(webView).track()

        // On internal builds native input is enabled, which disables the legacy omnibar field
        // and routes a tap to the unified input screen. Drive that flow — open the input screen
        // and type the URL into the native input field — instead of typing into the disabled
        // omnibar. Loading the warm-up page first also gives the privacy config time to load
        // before the test page fires its requests.
        // inputField lives in :duckchat-impl; resolve its id by name so we don't import an impl
        // R class (forbidden by the NoImplImportsInAppModule lint rule).
        val inputFieldId = inputFieldId()
        onView(withId(R.id.omnibarTextInputClickCatcher)).perform(click())
        onView(isRoot()).perform(waitFor(1000))
        onView(isRoot()).perform(waitForView(withId(inputFieldId)))
        onView(withId(inputFieldId)).perform(
            clearText(),
            typeText("https://privacy-test-pages.site/privacy-protections/request-blocklist/"),
            pressImeActionButton(),
        )

        WebViewIdlingResource(webView).track()

        // window.results won't exist until the request-blocklist page's finished() fires
        JsObjectIdlingResource(webView, "window.results").track()

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

    private fun inputFieldId(): Int {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.resources.getIdentifier("inputField", "id", context.packageName)
            .also { require(it != 0) { "inputField id not found in ${context.packageName}" } }
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

    private class WebViewGrabber : ViewAction {
        var webView: WebView? = null
        override fun getConstraints(): Matcher<View> = instanceOf(WebView::class.java)
        override fun getDescription(): String = "grab WebView reference"
        override fun perform(uiController: UiController, view: View) {
            webView = view as WebView
        }
    }
}
