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

package com.duckduckgo.app.browser.useragent

import com.duckduckgo.app.global.device.DeviceInfo
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserAgentProviderTest {

    private lateinit var testee: UserAgentProvider

    private var deviceInfo: DeviceInfo = mock()

    @Before
    fun before() {
        whenever(deviceInfo.majorAppVersion).thenReturn("5")
    }

    @Test
    fun whenUaRetrievedWithNoParamsThenDeviceStrippedAndApplicationComponentAddedBeforeSafari() {
        testee = UserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent()
        assertTrue("$actual does not match expected regex", ValidationRegex.converted.matches(actual))
    }

    @Test
    fun whenMobileUaRetrievedThenDeviceStrippedAndApplicationComponentAddedBeforeSafari() {
        testee = UserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(isDesktop = false)
        assertTrue("$actual does not match expected regex", ValidationRegex.converted.matches(actual))
    }

    @Test
    fun whenDesktopUaRetrievedThenDeviceStrippedAndApplicationComponentAddedBeforeSafari() {
        testee = UserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(isDesktop = true)
        assertTrue("$actual does not match expected regex", ValidationRegex.desktop.matches(actual))
    }

    @Test
    fun whenMissingAppleWebKitComponentThenUaContainsMozillaAndApplicationAndSafariComponents() {
        testee = UserAgentProvider(Agent.NO_WEBKIT, deviceInfo)
        val actual = testee.userAgent(isDesktop = false)
        assertTrue("$actual does not match expected regex", ValidationRegex.missingWebKit.matches(actual))
    }

    @Test
    fun whenMissingSafariComponentThenUaContainsMozillaAndVersionAndApplicationComponents() {
        testee = UserAgentProvider(Agent.NO_SAFARI, deviceInfo)
        val actual = testee.userAgent(isDesktop = false)
        assertTrue("$actual does not match expected result", ValidationRegex.missingSafari.matches(actual))
    }

    @Test
    fun whenMissingVersionComponentThenUaContainsMozillaAndApplicationAndSafariComponents() {
        testee = UserAgentProvider(Agent.NO_VERSION, deviceInfo)
        val actual = testee.userAgent(isDesktop = false)
        assertTrue("$actual does not match expected result", ValidationRegex.noVersion.matches(actual))
    }

    @Test
    fun whenDomainDoesNotSupportApplicationThenUaOmitsApplicationComponent() {
        testee = UserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(NO_APPLICATION_DOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.noApplication.matches(actual))
    }

    @Test
    fun whenSubdomsinDoesNotSupportApplicationThenUaOmitsApplicationComponent() {
        testee = UserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(NO_APPLICATION_SUBDOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.noApplication.matches(actual))
    }

    @Test
    fun whenDomainSupportsApplicationThenUaAddsApplicationComponentBeforeSafari() {
        testee = UserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.converted.matches(actual))
    }

    @Test
    fun whenDomainDoesNotSupportVersionThenUaOmitsVersionComponent() {
        testee = UserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(NO_VERSION_DOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.noVersion.matches(actual))
    }

    @Test
    fun whenSubdomainDoesNotSupportVersionThenUaOmitsVersionComponent() {
        testee = UserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(NO_VERSION_SUBDOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.noVersion.matches(actual))
    }

    @Test
    fun whenDomainSupportsVersionThenUaIncludesVersionComponentInUsualLocation() {
        testee = UserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.converted.matches(actual))
    }

    companion object {
        const val DOMAIN = "example.com"
        const val NO_APPLICATION_DOMAIN = "cvs.com"
        const val NO_APPLICATION_SUBDOMAIN = "subdomain.cvs.com"
        const val NO_VERSION_DOMAIN = "ing.nl"
        const val NO_VERSION_SUBDOMAIN = "subdomain.ing.nl"
    }

    private object Agent {
        const val DEFAULT =
            "Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/64.0.3282.137 Mobile Safari/537.36"
        const val NO_WEBKIT =
            "Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) Version/4.0 Chrome/64.0.3282.137 Mobile Safari/537.36"
        const val NO_SAFARI =
            "Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/64.0.3282.137 Mobile"
        const val NO_VERSION =
            "Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.137 Mobile Safari/537.36"
    }

    // Some values will be dynamic based on OS/Architecture/Software versions, so we use Regex to validate values
    private object ValidationRegex {
        val converted = Regex(
            "Mozilla/5.0 \\(Linux; Android .*?\\) AppleWebKit/[.0-9]+ \\(KHTML, like Gecko\\) Version/[.0-9]+ Chrome/[.0-9]+ Mobile DuckDuckGo/5 Safari/[.0-9]+"
        )
        val desktop = Regex(
            "Mozilla/5.0 \\(X11; Linux .*?\\) AppleWebKit/[.0-9]+ \\(KHTML, like Gecko\\) Version/[.0-9]+ Chrome/[.0-9]+ DuckDuckGo/5 Safari/[.0-9]+"
        )
        val noApplication = Regex(
            "Mozilla/5.0 \\(Linux; Android .*?\\) AppleWebKit/[.0-9]+ \\(KHTML, like Gecko\\) Version/[.0-9]+ Chrome/[.0-9]+ Mobile Safari/[.0-9]+"
        )
        val noVersion = Regex(
            "Mozilla/5.0 \\(Linux; Android .*?\\) AppleWebKit/[.0-9]+ \\(KHTML, like Gecko\\) Chrome/[.0-9]+ Mobile DuckDuckGo/5 Safari/[.0-9]+"
        )
        val missingWebKit = Regex(
            "Mozilla/5.0 \\(Linux; Android .*?\\) DuckDuckGo/5 Safari/[.0-9]+"
        )
        val missingSafari = Regex(
            "Mozilla/5.0 \\(Linux; Android .*?\\) AppleWebKit/[.0-9]+ \\(KHTML, like Gecko\\) Version/[.0-9]+ Chrome/[.0-9]+ Mobile DuckDuckGo/5"
        )
    }
}
