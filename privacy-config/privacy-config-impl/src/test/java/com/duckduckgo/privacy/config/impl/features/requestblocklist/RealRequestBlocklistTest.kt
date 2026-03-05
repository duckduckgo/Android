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

package com.duckduckgo.privacy.config.impl.features.requestblocklist

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealRequestBlocklistTest {

    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    private val mockToggle: Toggle = mock()
    private val mockFeature: RequestBlocklistFeature = mock<RequestBlocklistFeature>().apply {
        whenever(self()).thenReturn(mockToggle)
    }

    private fun createTestee(settings: String? = null, isMainProcess: Boolean = true, featureEnabled: Boolean = true): RealRequestBlocklist {
        whenever(mockToggle.getSettings()).thenReturn(settings)
        whenever(mockToggle.isEnabled()).thenReturn(featureEnabled)
        return RealRequestBlocklist(
            requestBlocklistFeature = mockFeature,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            isMainProcess = isMainProcess,
            appCoroutineScope = coroutineTestRule.testScope,
        )
    }

    @Test
    fun whenFeatureIsDisabledThenReturnFalse() {
        val testee = createTestee(settingsWithRule("reuters.com/*.jpg", listOf("example.com")), featureEnabled = false)

        assertFalse(testee.containedInBlocklist("https://example.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenRequestMatchesRuleAndDomainThenReturnTrue() {
        val testee = createTestee(settingsWithRule("reuters.com/*.jpg", listOf("example.com")))

        assertTrue(testee.containedInBlocklist("https://example.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenRequestMatchesRuleButDomainDoesNotMatchThenReturnFalse() {
        val testee = createTestee(settingsWithRule("reuters.com/*.jpg", listOf("example.com")))

        assertFalse(testee.containedInBlocklist("https://other.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenRequestDoesNotMatchRuleThenReturnFalse() {
        val testee = createTestee(settingsWithRule("reuters.com/*.jpg", listOf("example.com")))

        assertFalse(testee.containedInBlocklist("https://example.com", "https://reuters.com/article.html"))
    }

    @Test
    fun whenRequestDomainHasNoRulesThenReturnFalse() {
        val testee = createTestee(settingsWithRule("reuters.com/*.jpg", listOf("example.com")))

        assertFalse(testee.containedInBlocklist("https://example.com", "https://other.com/image.jpg"))
    }

    @Test
    fun whenSettingsAreNullThenReturnFalse() {
        val testee = createTestee(settings = null)

        assertFalse(testee.containedInBlocklist("https://example.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenRequestUrlIsInvalidThenReturnFalse() {
        val testee = createTestee(settingsWithRule("reuters.com/*.jpg", listOf("example.com")))

        assertFalse(testee.containedInBlocklist("https://example.com", "not a url"))
    }

    @Test
    fun whenDomainsContainsAllMarkerThenReturnTrueForAnyDocument() {
        val testee = createTestee(settingsWithRule("reuters.com/*.jpg", listOf("<all>")))

        assertTrue(testee.containedInBlocklist("https://any-site.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenDocumentUrlIsSubdomainOfRuleDomainThenReturnTrue() {
        val testee = createTestee(settingsWithRule("reuters.com/*.jpg", listOf("example.com")))

        assertTrue(testee.containedInBlocklist("https://sub.example.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenWildcardMatchesMultipleSegmentsThenReturnTrue() {
        val testee = createTestee(settingsWithRule("reuters.com/*/image.jpg", listOf("example.com")))

        assertTrue(testee.containedInBlocklist("https://example.com", "https://reuters.com/path/to/image.jpg"))
    }

    @Test
    fun whenMultipleRulesExistAndSecondMatchesThenReturnTrue() {
        val settings = """
            {
                "blockedRequests": {
                    "reuters.com": {
                        "rules": [
                            {"rule": "reuters.com/*.png", "domains": ["example.com"], "reason": "breakage"},
                            {"rule": "reuters.com/*.jpg", "domains": ["example.com"], "reason": "breakage"}
                        ]
                    }
                }
            }
        """.trimIndent()
        val testee = createTestee(settings)

        assertTrue(testee.containedInBlocklist("https://example.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenRuleHasUnknownPropertyThenRuleIsSkipped() {
        val settings = """
            {
                "blockedRequests": {
                    "reuters.com": {
                        "rules": [
                            {"rule": "reuters.com/*.jpg", "domains": ["example.com"], "reason": "breakage", "unknownField": true}
                        ]
                    }
                }
            }
        """.trimIndent()
        val testee = createTestee(settings)

        assertFalse(testee.containedInBlocklist("https://example.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenRuleMissingRequiredFieldsThenRuleIsSkipped() {
        val settings = """
            {
                "blockedRequests": {
                    "reuters.com": {
                        "rules": [
                            {"rule": "reuters.com/*.jpg"}
                        ]
                    }
                }
            }
        """.trimIndent()
        val testee = createTestee(settings)

        assertFalse(testee.containedInBlocklist("https://example.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenSettingsJsonIsMalformedThenReturnFalse() {
        val testee = createTestee(settings = "not valid json")

        assertFalse(testee.containedInBlocklist("https://example.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenConfigDomainIsNotTopPrivateDomainThenRulesAreIgnored() {
        val settings = """
            {
                "blockedRequests": {
                    "sub.reuters.com": {
                        "rules": [
                            {"rule": "sub.reuters.com/*.jpg", "domains": ["example.com"], "reason": "breakage"}
                        ]
                    }
                }
            }
        """.trimIndent()
        val testee = createTestee(settings)

        assertFalse(testee.containedInBlocklist("https://example.com", "https://sub.reuters.com/image.jpg"))
    }

    @Test
    fun whenOnPrivacyConfigDownloadedThenRulesAreReloaded() {
        val testee = createTestee(settings = null)
        assertFalse(testee.containedInBlocklist("https://example.com", "https://reuters.com/image.jpg"))

        whenever(mockToggle.getSettings()).thenReturn(settingsWithRule("reuters.com/*.jpg", listOf("example.com")))
        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.containedInBlocklist("https://example.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenNotMainProcessThenRulesAreNotLoadedOnInit() {
        whenever(mockToggle.getSettings()).thenReturn(settingsWithRule("reuters.com/*.jpg", listOf("example.com")))
        val testee = RealRequestBlocklist(
            requestBlocklistFeature = mockFeature,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            isMainProcess = false,
            appCoroutineScope = coroutineTestRule.testScope,
        )

        assertFalse(testee.containedInBlocklist("https://example.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenDocumentUrlIsEmptyThenReturnFalse() {
        val testee = createTestee(settingsWithRule("reuters.com/*.jpg", listOf("example.com")))

        assertFalse(testee.containedInBlocklist("", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenMultipleDomainsInRuleAndOneMatchesThenReturnTrue() {
        val testee = createTestee(settingsWithRule("reuters.com/*.jpg", listOf("example.com", "test.com")))

        assertTrue(testee.containedInBlocklist("https://test.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenBlockedRequestsMapHasMultipleDomainsAndRequestMatchesOneThenReturnTrue() {
        val settings = """
            {
                "blockedRequests": {
                    "reuters.com": {
                        "rules": [
                            {"rule": "reuters.com/*.jpg", "domains": ["example.com"], "reason": "breakage"}
                        ]
                    },
                    "cdn.com": {
                        "rules": [
                            {"rule": "cdn.com/*.js", "domains": ["test.com"], "reason": "breakage"}
                        ]
                    }
                }
            }
        """.trimIndent()
        val testee = createTestee(settings)

        assertTrue(testee.containedInBlocklist("https://test.com", "https://cdn.com/script.js"))
        assertFalse(testee.containedInBlocklist("https://test.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenRuleHasDifferentCaseThanRequestThenCaseSensitiveMatchFails() {
        val testee = createTestee(settingsWithRule("reuters.com/*.JPG", listOf("example.com")))

        assertFalse(testee.containedInBlocklist("https://example.com", "https://reuters.com/image.jpg"))
    }

    @Test
    fun whenRuleAndRequestHaveSameCaseThenReturnTrue() {
        val testee = createTestee(settingsWithRule("reuters.com/*.JPG", listOf("example.com")))

        assertTrue(testee.containedInBlocklist("https://example.com", "https://reuters.com/image.JPG"))
    }

    private fun settingsWithRule(rule: String, domains: List<String>): String {
        val domain = rule.substringBefore("/")
        val domainsJson = domains.joinToString(",") { "\"$it\"" }
        return """
            {
                "blockedRequests": {
                    "$domain": {
                        "rules": [
                            {"rule": "$rule", "domains": [$domainsJson], "reason": "site breakage"}
                        ]
                    }
                }
            }
        """.trimIndent()
    }
}