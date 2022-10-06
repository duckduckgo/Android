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
import androidx.core.net.toUri
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
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.espresso.PrivacyTest
import com.duckduckgo.espresso.WebViewIdlingResource
import com.duckduckgo.espresso.waitForView
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HttpsUpgradesTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<BrowserActivity>(
        BrowserActivity.intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            queryExtra = "http://privacy-test-pages.glitch.me/privacy-protections/https-upgrades/"
        )
    )

    @Test @PrivacyTest
    fun whenProtectionsAreEnabledHttpsUpgradedCorrectly() {
        val waitTime = 16000L
        IdlingPolicies.setMasterPolicyTimeout(waitTime * 10, TimeUnit.MILLISECONDS)
        IdlingPolicies.setIdlingResourceTimeout(waitTime * 10, TimeUnit.MILLISECONDS)

        var webView: WebView? = null

        onView(isRoot()).perform(waitForView(withId(R.id.pageLoadingIndicator)))

        activityScenarioRule.scenario.onActivity {
            webView = it.findViewById(R.id.browserWebView)
        }

        val idlingResourceForDisableProtections = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForDisableProtections)

        onWebView()
            .withElement(findElement(ID, "start"))
            .check(webMatches(getText(), containsString("Start test")))
            .perform(webClick())

        val idlingResourceForScript = WebViewIdlingResource(webView!!)
        IdlingRegistry.getInstance().register(idlingResourceForScript)

        val results = onWebView()
            .perform(script(SCRIPT))
            .get()

        val testJson: TestJson? = getTestJson(results.toJSONString())
        testJson?.value?.map {
            if (compatibleIds.contains(it.id)) {
                assertTrue("Url for ${it.id} should be https", it.value?.toUri()?.isHttps ?: false)
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
        val compatibleIds = listOf("upgrade-navigation")
    }

    data class TestJson(val status: Int, val value: List<HttpsUpgradeTest>)
    data class HttpsUpgradeTest(val id: String, val value: String?)
}
