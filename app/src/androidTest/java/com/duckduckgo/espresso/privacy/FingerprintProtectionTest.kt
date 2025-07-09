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
import androidx.test.espresso.matcher.ViewMatchers.withId
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FingerprintProtectionTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<BrowserActivity>(
        BrowserActivity.intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "https://privacy-test-pages.site/privacy-protections",
        ),
    )

    private val registeredResources = mutableListOf<IdlingResource>()

    @Test @PrivacyTest
    fun whenProtectionsAreFingerprintProtected() {
        preparationsForPrivacyTest()

        var webView: WebView? = null
        activityScenarioRule.scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }

        WebViewIdlingResource(webView!!).track()
        onView(withId(R.id.omnibarTextInput)).perform(
            typeText("https://privacy-test-pages.site/privacy-protections/fingerprinting/?disable_tests=navigator.requestMediaKeySystemAccess"),
            pressImeActionButton(),
        )
        WebViewIdlingResource(webView!!).track()

        // asserts we have injected css by querying the duckduckgo object
        JsObjectIdlingResource(webView!!, "window.navigator.duckduckgo").track()

        onWebView()
            .withElement(findElement(ID, "start"))
            .check(webMatches(getText(), containsString("Start the test")))
            .perform(webClick())

        WebViewIdlingResource(webView!!).track()

        val results = onWebView()
            .perform(script(SCRIPT))
            .get()

        val testJson: TestJson? = getTestJson(results.toJSONString())
        testJson?.value?.map {
            if (it.id.contains("navigator.webkitTemporaryStorage.queryUsageAndQuota")) {
                val quota = extractQuota(it.value.toString())!!
                assertTrue(quota <= MAX_QUOTA) // CSS limits quota to 4gb, we check that it is not exceeded
            }
            if (compatibleIds.contains(it.id)) {
                val expected = compatibleIds[it.id]!!
                val actual = it.value.toString()
                assertEquals(sortProperties(expected), sortProperties(actual))
            }
        }
    }

    @After
    fun unregisterIdlingResources() {
        registeredResources.forEach { IdlingRegistry.getInstance().unregister(it) }
    }

    private fun IdlingResource.track() = apply {
        registeredResources += this
        IdlingRegistry.getInstance().register(this)
    }

    private fun getTestJson(jsonString: String): TestJson? {
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val jsonAdapter: JsonAdapter<TestJson> = moshi.adapter(TestJson::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    private fun sortProperties(value: String): String {
        return if (value.startsWith("{") && value.endsWith("}")) {
            value.trim('{', '}')
                .split(", ")
                .sorted()
                .joinToString(prefix = "{", postfix = "}", separator = ", ")
        } else {
            value
        }
    }

    private fun extractQuota(input: String): Double? {
        // Find where "quota=" appears
        val key = "quota="
        val startIndex = input.indexOf(key)
        if (startIndex == -1) return null

        // Compute where the number itself starts
        val numberStart = startIndex + key.length

        // Find the end of the number (either at the comma or the closing brace)
        val commaIndex = input.indexOf(',', numberStart)
        val endIndex = if (commaIndex != -1) commaIndex else input.indexOf('}', numberStart).takeIf { it != -1 } ?: input.length

        // Extract and parse
        val numberString = input.substring(numberStart, endIndex).trim()
        return numberString.toDoubleOrNull()
    }

    companion object {
        const val SCRIPT = "return results.results;"
        const val MAX_QUOTA = 4L * 1_024 * 1_024 * 1_024
        val compatibleIds = mapOf(
            Pair("navigator.deviceMemory", "4.0"),
            Pair("navigator.hardwareConcurrency", "8.0"),
            Pair("navigator.getBattery()", "{level=1.0, chargingTime=0.0, charging=true}"),
            Pair("screen.colorDepth", "24.0"),
            Pair("screen.pixelDepth", "24.0"),
            Pair("screen.availLeft", "0.0"),
            Pair("screen.availTop", "0.0"),
        )
    }

    data class TestJson(
        val status: Int,
        val value: List<FingerProtectionTest>,
    )

    data class FingerProtectionTest(
        val id: String,
        val value: Any,
    )
}
