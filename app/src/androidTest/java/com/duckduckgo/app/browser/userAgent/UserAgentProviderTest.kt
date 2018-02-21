/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser.userAgent

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val CHROME_UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.137 Mobile Safari/537.36"

// Some values will be dynamic based on OS/Architecture/Software versions, so use Regex to match around dynamic values
private val CHROME_UA_DESKTOP_REGEX = Regex(
        "Mozilla/5.0 \\(X11; Linux .*?\\) AppleWebKit\\/[.0-9]+ \\(KHTML, like Gecko\\) Chrome\\/[.0-9]+ Safari/[.0-9]+"
)

class UserAgentProviderTest {

    private lateinit var testee: UserAgentProvider

    @Before
    fun setup() {
        testee = UserAgentProvider()
    }

    @Test
    fun whenMobileUaRetrievedThenDeviceStrippedFromReturnedUa() {
        val actual = testee.getUserAgent(CHROME_UA_MOBILE, desktopSiteRequested = false)
        val expected = "Mozilla/5.0 (Linux; Android 8.1.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.137 Mobile Safari/537.36"
        assertEquals(expected, actual)
    }

    @Test
    fun whenDesktopUaRetrievedThenDeviceStrippedFromReturnedUa() {
        val actual = testee.getUserAgent(CHROME_UA_MOBILE, desktopSiteRequested = true)
        CHROME_UA_DESKTOP_REGEX.matches(actual)
    }

    @Test
    fun whenMissingAppleWebKitStringThenSimplyReturnsNothing() {
        val missingAppleWebKitPart = "Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) Chrome/64.0.3282.137 Mobile Safari/537.36"
        val expected = "Mozilla/5.0 (Linux; Android 8.1.0)"
        val actual = testee.getUserAgent(missingAppleWebKitPart, desktopSiteRequested = false)
        assertEquals(expected, actual)
    }
}