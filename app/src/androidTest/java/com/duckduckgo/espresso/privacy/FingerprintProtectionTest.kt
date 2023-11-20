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
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
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
import com.duckduckgo.espresso.*
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FingerprintProtectionTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<BrowserActivity>()

    @Test @PrivacyTest
    fun whenProtectionsAreFingerprintProtected() {
        val waitTime = 16000L
        IdlingPolicies.setMasterPolicyTimeout(waitTime * 10, TimeUnit.MILLISECONDS)
        IdlingPolicies.setIdlingResourceTimeout(waitTime * 10, TimeUnit.MILLISECONDS)

        var webView: WebView? = null

        onView(isRoot()).perform(waitForView(withId(R.id.browserMenu)))
        onView(isRoot()).perform(waitFor(2000))

        val scenario = ActivityScenario.launch<BrowserActivity>(
            BrowserActivity.intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                "https://privacy-test-pages.site/privacy-protections/fingerprinting/?disable_tests=navigator.requestMediaKeySystemAccess",
            ),
        )
        scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }

        val idlingResourceForDisableProtections = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForDisableProtections)

        onWebView()
            .withElement(findElement(ID, "start"))
            .check(webMatches(getText(), containsString("Start the test")))
            .perform(webClick())

        val idlingResourceForScript = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForScript)

        val results = onWebView()
            .perform(script(SCRIPT))
            .get()

        val testJson: TestJson? = getTestJson(results.toJSONString())
        testJson?.value?.map {
            if (compatibleIds.contains(it.id)) {
                assertEquals(compatibleIds[it.id], it.value.toString())
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
        val compatibleIds = mapOf(
            Pair("navigator.deviceMemory", "4.0"),
            Pair("navigator.hardwareConcurrency", "8.0"),
            Pair("navigator.getBattery()", "{level=1.0, chargingTime=0.0, charging=true}"),
            Pair("navigator.webkitTemporaryStorage.queryUsageAndQuota", "{quota=4.294967296E9, usage=0.0}"),
            Pair("screen.colorDepth", "24.0"),
            Pair("screen.pixelDepth", "24.0"),
            Pair("screen.availLeft", "0.0"),
            Pair("screen.availTop", "0.0"),
        )
    }

    data class TestJson(val status: Int, val value: List<FingerProtectionTest>)
    data class FingerProtectionTest(val id: String, val value: Any)
}
