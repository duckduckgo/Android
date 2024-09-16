/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.user.agent.impl

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.user.agent.api.UserAgentInterceptor
import com.duckduckgo.user.agent.api.UserAgentProvider
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandHintFeature
import com.duckduckgo.user.agent.store.UserAgentFeatureName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class UserAgentProviderTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: UserAgentProvider

    private var deviceInfo: DeviceInfo = mock()
    private var userAgent: UserAgent = mock()
    private var toggle: FeatureToggle = mock()
    private var toggles: Toggle = mock()
    private var clientBrandHintFeature: ClientBrandHintFeature = mock()

    @Before
    fun before() {
        whenever(deviceInfo.majorAppVersion).thenReturn("5")
        whenever(toggle.isFeatureEnabled(UserAgentFeatureName.UserAgent.value)).thenReturn(true)
        whenever(clientBrandHintFeature.self()).thenReturn(toggles)
        whenever(clientBrandHintFeature.self().isEnabled()).thenReturn(false)
        whenever(userAgent.isException("default.com")).thenReturn(true)
        whenever(userAgent.isException("unprotected.com")).thenReturn(true)
        whenever(userAgent.isException("subdomain.default.com")).thenReturn(true)
        whenever(userAgent.isException("subdomain.unprotected.com")).thenReturn(true)
        whenever(userAgent.useLegacyUserAgent(anyString())).thenReturn(true)

        System.setProperty("os.arch", "aarch64")
    }

    @After fun tearDown() {
        System.clearProperty("os.arch")
    }

    @Test
    fun whenUaRetrievedWithNoParamsThenDeviceStrippedAndApplicationComponentAddedBeforeSafari() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.converted.matches(actual))
    }

    @Test
    fun whenMobileUaRetrievedThenDeviceStrippedAndApplicationComponentAddedBeforeSafari() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN, isDesktop = false)
        assertTrue("$actual does not match expected regex", ValidationRegex.converted.matches(actual))
    }

    @Test
    fun whenDesktopUaRetrievedThenDeviceStrippedAndApplicationComponentAddedBeforeSafari() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN, isDesktop = true)
        assertTrue("$actual does not match expected regex", ValidationRegex.desktop.matches(actual))
    }

    @Test
    fun whenMissingAppleWebKitComponentThenUaContainsMozillaAndApplicationAndSafariComponents() {
        testee = getUserAgentProvider(Agent.NO_WEBKIT, deviceInfo)
        val actual = testee.userAgent(DOMAIN, isDesktop = false)
        assertTrue("$actual does not match expected regex", ValidationRegex.missingWebKit.matches(actual))
    }

    @Test
    fun whenMissingSafariComponentThenUaContainsMozillaAndVersionAndApplicationComponents() {
        testee = getUserAgentProvider(Agent.NO_SAFARI, deviceInfo)
        val actual = testee.userAgent(DOMAIN, isDesktop = false)
        assertTrue("$actual does not match expected result", ValidationRegex.missingSafari.matches(actual))
    }

    @Test
    fun whenMissingVersionComponentThenUaContainsMozillaAndApplicationAndSafariComponents() {
        testee = getUserAgentProvider(Agent.NO_VERSION, deviceInfo)
        val actual = testee.userAgent(DOMAIN, isDesktop = false)
        assertTrue("$actual does not match expected result", ValidationRegex.noVersion.matches(actual))
    }

    @Test
    fun whenDomainSupportsApplicationThenUaAddsApplicationComponentBeforeSafari() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.converted.matches(actual))
    }

    @Test
    fun whenDomainHasExceptionThenUaIsDefault() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DEFAULT_DOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.default.matches(actual))
    }

    @Test
    fun whenSubdomainHasExceptionThenUaIsDefault() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DEFAULT_SUBDOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.default.matches(actual))
    }

    @Test
    fun whenDomainInUnprotectedTemporaryThenUaIsDefault() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(UNPROTECTED_DOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.default.matches(actual))
    }

    @Test
    fun whenSubdomainInUnprotectedTemporaryThenUaIsDefault() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(UNPROTECTED_SUBDOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.default.matches(actual))
    }

    @Test
    fun whenFeatureIsDisabledThenUaIsDefault() {
        whenever(toggle.isFeatureEnabled(UserAgentFeatureName.UserAgent.value)).thenReturn(false)
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.default.matches(actual))
    }

    @Test
    fun whenDomainSupportsVersionThenUaIncludesVersionComponentInUsualLocation() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN)
        assertTrue("$actual does not match expected regex", ValidationRegex.converted.matches(actual))
    }

    @Test
    fun whenUserAgentIsForASiteThatShouldUseDesktopAgentThenReturnDesktopUserAgent() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DESKTOP_ONLY_SITE)
        assertTrue("$actual does not match expected regex", ValidationRegex.desktop.matches(actual))
    }

    @Test
    fun whenUserAgentIsForASiteThatShouldUseDesktopAgentButContainsAnExclusionThenDoNotReturnConvertedUserAgent() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DESKTOP_ONLY_SITE_EXCEPTION)
        assertTrue("$actual does not match expected regex", ValidationRegex.converted.matches(actual))
    }

    @Test
    fun whenUserAgentShouldBeDefaultAndShouldUseDesktopAgentThenReturnDesktopUserAgent() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DEFAULT_DOMAIN, true)
        assertTrue("$actual does not match expected regex", ValidationRegex.desktop_default.matches(actual))
    }

    @Test
    fun whenUserAgentAndUrlAllowedByUserThenReturnDefaultUserAgent() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(ALLOWED_URL, false)
        assertTrue("$actual does not match expected regex", ValidationRegex.default.matches(actual))
    }

    @Test
    fun whenUserAgentAndUrlAllowedByUserAndIsDesktopThenReturnDefaultDesktopUserAgent() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(ALLOWED_URL, true)
        assertTrue("$actual does not match expected regex", ValidationRegex.desktop_default.matches(actual))
    }

    @Test
    fun whenIsLegacySiteThenReturnLegacyUserAgent() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN, false)
        assertTrue("$actual does not match expected regex", ValidationRegex.converted.matches(actual))
    }

    @Test
    fun whenIsLegacySiteAndIsDesktopThenReturnDesktopLegacyUserAgent() {
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN, true)
        assertTrue("$actual does not match expected regex", ValidationRegex.desktop.matches(actual))
    }

    @Test
    fun whenHasNoExceptionsThenReturnUserAgent() {
        whenever(userAgent.useLegacyUserAgent(anyString())).thenReturn(false)
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN, false)
        assertTrue("$actual does not match expected regex", ValidationRegex.userAgent.matches(actual))
    }

    @Test
    fun whenIsDesktopThenReturnDesktopUserAgent() {
        whenever(userAgent.useLegacyUserAgent(anyString())).thenReturn(false)
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN, true)
        assertTrue("$actual does not match expected regex", ValidationRegex.userAgentDesktop.matches(actual))
    }

    @Test
    fun whenIsDesktopAndIsAarch64ThenReturnX86_64DesktopUserAgent() {
        whenever(userAgent.useLegacyUserAgent(anyString())).thenReturn(false)
        testee = getUserAgentProvider(Agent.DEFAULT, deviceInfo)
        val actual = testee.userAgent(DOMAIN, true)
        assertTrue("$actual does not match expected regex", ValidationRegex.userAgentDesktopArch.matches(actual))
    }

    private fun getUserAgentProvider(
        defaultUserAgent: String,
        device: DeviceInfo,
        userAgentInterceptorPluginPoint: PluginPoint<UserAgentInterceptor> = provideUserAgentFakePluginPoint(),
    ): UserAgentProvider {
        return RealUserAgentProvider(
            { defaultUserAgent },
            device,
            userAgentInterceptorPluginPoint,
            userAgent,
            toggle,
            FakeUserAllowListRepo(),
        )
    }

    internal class FakeUserAllowListRepo : UserAllowListRepository {
        override fun isUrlInUserAllowList(url: String): Boolean = false

        override fun isUriInUserAllowList(uri: Uri): Boolean = false

        override fun isDomainInUserAllowList(domain: String?): Boolean = (domain == ALLOWED_HOST)

        override fun domainsInUserAllowList(): List<String> = emptyList()

        override fun domainsInUserAllowListFlow(): Flow<List<String>> = flowOf(emptyList())

        override suspend fun addDomainToUserAllowList(domain: String) = Unit

        override suspend fun removeDomainFromUserAllowList(domain: String) = Unit
    }

    companion object {
        const val DOMAIN = "http://example.com"
        const val DEFAULT_DOMAIN = "http://default.com"
        const val DEFAULT_SUBDOMAIN = "http://subdomain.default.com"
        const val UNPROTECTED_DOMAIN = "http://unprotected.com"
        const val UNPROTECTED_SUBDOMAIN = "http://subdomain.unprotected.com"
        const val DESKTOP_ONLY_SITE = "http://m.facebook.com"
        const val DESKTOP_ONLY_SITE_EXCEPTION = "http://m.facebook.com/dialog/"
        const val ALLOWED_URL = "http://allowed.com"
        const val ALLOWED_HOST = "allowed.com"
    }

    private object Agent {
        const val DEFAULT =
            "Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/64.0.3282.137 Mobile Safari/537.36"
        const val NO_WEBKIT =
            "Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) Version/4.0 Chrome/64.0.3282.137 Mobile Safari/537.36"
        const val NO_SAFARI =
            "Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/64.0.3282.137 Mobile"
        const val NO_VERSION =
            "Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/64.0.3282.137 Mobile Safari/537.36"
    }

    // Some values will be dynamic based on OS/Architecture/Software versions, so we use Regex to validate values
    private object ValidationRegex {
        val default = Regex(
            "Mozilla/5.0 \\(Linux; Android .*? Nexus 6P Build/OPM3.171019.014\\) AppleWebKit/[.0-9]+" +
                " \\(KHTML, like Gecko\\) Version/[.0-9]+ Chrome/[.0-9]+ Mobile Safari/[.0-9]+",
        )
        val desktop_default = Regex(
            "Mozilla/5.0 \\(X11; Linux .*? Nexus 6P Build/OPM3.171019.014\\) AppleWebKit/[.0-9]+" +
                " \\(KHTML, like Gecko\\) Version/[.0-9]+ Chrome/[.0-9]+ Mobile Safari/[.0-9]+",
        )
        val converted = Regex(
            "Mozilla/5.0 \\(Linux; Android .*?\\) AppleWebKit/[.0-9]+" +
                " \\(KHTML, like Gecko\\) Version/[.0-9]+ Chrome/(\\d+)\\.0\\.0\\.0 Mobile DuckDuckGo/5 Safari/[.0-9]+",
        )
        val desktop = Regex(
            "Mozilla/5.0 \\(X11; Linux .*?\\) AppleWebKit/[.0-9]+ " +
                "\\(KHTML, like Gecko\\) Version/[.0-9]+ Chrome/(\\d+)\\.0\\.0\\.0 DuckDuckGo/5 Safari/[.0-9]+",
        )
        val noVersion = Regex(
            "Mozilla/5.0 \\(Linux; Android .*?\\) AppleWebKit/[.0-9]+ " +
                "\\(KHTML, like Gecko\\) Chrome/(\\d+)\\.0\\.0\\.0 Mobile DuckDuckGo/5 Safari/[.0-9]+",
        )
        val missingWebKit = Regex(
            "Mozilla/5.0 \\(Linux; Android .*?\\) DuckDuckGo/5 Safari/[.0-9]+",
        )
        val missingSafari = Regex(
            "Mozilla/5.0 \\(Linux; Android .*?\\) AppleWebKit/[.0-9]+ " +
                "\\(KHTML, like Gecko\\) Version/[.0-9]+ Chrome/(\\d+)\\.0\\.0\\.0 Mobile DuckDuckGo/5",
        )
        val userAgent = Regex(
            "Mozilla/5.0 \\(Linux; Android 10; K\\) AppleWebKit/[.0-9]+ " +
                "\\(KHTML, like Gecko\\) Chrome/(\\d+)\\.0\\.0\\.0 Mobile Safari/[.0-9]+",
        )
        val userAgentDesktop = Regex(
            "Mozilla/5.0 \\(X11; Linux .*?\\) AppleWebKit/[.0-9]+ " +
                "\\(KHTML, like Gecko\\) Chrome/(\\d+)\\.0\\.0\\.0 Safari/[.0-9]+",
        )
        val userAgentDesktopArch = Regex(
            "Mozilla/5.0 \\(X11; Linux x86_64\\) AppleWebKit/[.0-9]+ " +
                "\\(KHTML, like Gecko\\) Chrome/(\\d+)\\.0\\.0\\.0 Safari/[.0-9]+",
        )
    }
}
