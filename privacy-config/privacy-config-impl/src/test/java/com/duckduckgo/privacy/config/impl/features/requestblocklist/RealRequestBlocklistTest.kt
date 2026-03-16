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
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.moshi.Moshi
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

    private val moshi = Moshi.Builder().build()
    private val mockFeature: RequestBlocklistFeature = mock<RequestBlocklistFeature>().apply {
        whenever(self()).thenReturn(mockToggle)
    }

    private fun createTestee(
        settings: String? = null,
        isMainProcess: Boolean = true,
        featureEnabled: Boolean = true,
        exceptions: List<FeatureException> = emptyList(),
    ): RealRequestBlocklist {
        whenever(mockToggle.getSettings()).thenReturn(settings)
        whenever(mockToggle.isEnabled()).thenReturn(featureEnabled)
        whenever(mockToggle.getExceptions()).thenReturn(exceptions)
        return RealRequestBlocklist(
            requestBlocklistFeature = mockFeature,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            isMainProcess = isMainProcess,
            appCoroutineScope = coroutineTestRule.testScope,
            moshi = moshi,
        )
    }

    @Test
    fun whenFeatureIsDisabledThenReturnFalse() {
        val testee =
            createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/*.jpg", domains = listOf("example.com")), featureEnabled = false)

        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenRequestMatchesRuleAndDomainThenReturnTrue() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/*.jpg", domains = listOf("example.com")))

        assertTrue(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenRequestMatchesRuleButDomainDoesNotMatchThenReturnFalse() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/*.jpg", domains = listOf("example.com")))

        assertFalse(testee.containedInBlocklist("https://other.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenRequestDoesNotMatchRuleThenReturnFalse() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/*.jpg", domains = listOf("example.com")))

        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/article.html"))
    }

    @Test
    fun whenRequestDomainHasNoRulesThenReturnFalse() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/*.jpg", domains = listOf("example.com")))

        assertFalse(testee.containedInBlocklist("https://example.com", "https://other.com/image.jpg"))
    }

    @Test
    fun whenSettingsAreNullThenReturnFalse() {
        val testee = createTestee(settings = null)

        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenRequestUrlIsInvalidThenReturnFalse() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/*.jpg", domains = listOf("example.com")))

        assertFalse(testee.containedInBlocklist("https://example.com", "not a url"))
    }

    @Test
    fun whenDomainsContainsAllMarkerThenReturnTrueForAnyDocument() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/*.jpg", domains = listOf("<all>")))

        assertTrue(testee.containedInBlocklist("https://any-site.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenDocumentUrlIsSubdomainOfRuleDomainThenReturnTrue() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/*.jpg", domains = listOf("example.com")))

        assertTrue(testee.containedInBlocklist("https://sub.example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenWildcardMatchesSingleSegmentThenReturnTrue() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/*/image.jpg", domains = listOf("example.com")))

        assertTrue(testee.containedInBlocklist("https://example.com", "https://testing.com/path/image.jpg"))
    }

    @Test
    fun whenWildcardDoesNotMatchMultipleSegmentsThenReturnFalse() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/*/image.jpg", domains = listOf("example.com")))

        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/path/to/image.jpg"))
    }

    @Test
    fun whenMultipleRulesExistAndSecondMatchesThenReturnTrue() {
        val settings = """
            {
                "blockedRequests": {
                    "testing.com": {
                        "rules": [
                            {"rule": "testing.com/*.png", "domains": ["example.com"], "reason": "breakage"},
                            {"rule": "testing.com/*.jpg", "domains": ["example.com"], "reason": "breakage"}
                        ]
                    }
                }
            }
        """.trimIndent()
        val testee = createTestee(settings)

        assertTrue(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenRuleHasUnknownPropertyThenRuleIsSkipped() {
        val settings = """
            {
                "blockedRequests": {
                    "testing.com": {
                        "rules": [
                            {"rule": "testing.com/*.jpg", "domains": ["example.com"], "reason": "breakage", "unknownField": true}
                        ]
                    }
                }
            }
        """.trimIndent()
        val testee = createTestee(settings)

        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenRuleMissingRequiredFieldsThenRuleIsSkipped() {
        val settings = """
            {
                "blockedRequests": {
                    "testing.com": {
                        "rules": [
                            {"rule": "testing.com/*.jpg"}
                        ]
                    }
                }
            }
        """.trimIndent()
        val testee = createTestee(settings)

        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenSettingsJsonIsMalformedThenReturnFalse() {
        val testee = createTestee(settings = "not valid json")

        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenConfigDomainIsNotTopPrivateDomainThenRulesAreIgnored() {
        val settings = """
            {
                "blockedRequests": {
                    "sub.testing.com": {
                        "rules": [
                            {"rule": "sub.testing.com/*.jpg", "domains": ["example.com"], "reason": "breakage"}
                        ]
                    }
                }
            }
        """.trimIndent()
        val testee = createTestee(settings)

        assertFalse(testee.containedInBlocklist("https://example.com", "https://sub.testing.com/image.jpg"))
    }

    @Test
    fun whenOnPrivacyConfigDownloadedThenRulesAreReloaded() {
        val testee = createTestee(settings = null)
        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))

        whenever(mockToggle.getSettings()).thenReturn(
            settingsWithRule(
                entry = "testing.com",
                rule = "testing.com/*.jpg",
                domains = listOf("example.com"),
            ),
        )
        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenNotMainProcessThenRulesAreNotLoadedOnInit() {
        whenever(mockToggle.getSettings()).thenReturn(
            settingsWithRule(
                entry = "testing.com",
                rule = "testing.com/*.jpg",
                domains = listOf("example.com"),
            ),
        )
        val testee = RealRequestBlocklist(
            requestBlocklistFeature = mockFeature,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            isMainProcess = false,
            appCoroutineScope = coroutineTestRule.testScope,
            moshi = moshi,
        )

        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenDocumentUrlIsEmptyThenReturnFalse() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/*.jpg", domains = listOf("example.com")))

        assertFalse(testee.containedInBlocklist("", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenMultipleDomainsInRuleAndOneMatchesThenReturnTrue() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/*.jpg", domains = listOf("example.com", "test.com")))

        assertTrue(testee.containedInBlocklist("https://test.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenBlockedRequestsMapHasMultipleDomainsAndRequestMatchesOneThenReturnTrue() {
        val settings = """
            {
                "blockedRequests": {
                    "testing.com": {
                        "rules": [
                            {"rule": "testing.com/*.jpg", "domains": ["example.com"], "reason": "breakage"}
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
        assertFalse(testee.containedInBlocklist("https://test.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenRuleMatchesQueryStringThenReturnTrue() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/path?hello=1", domains = listOf("example.com")))

        assertTrue(testee.containedInBlocklist("https://example.com", "https://testing.com/path?hello=1"))
    }

    @Test
    fun whenRuleMatchesQueryStringButRequestHasNoQueryThenReturnFalse() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/path?hello", domains = listOf("example.com")))

        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/path"))
    }

    @Test
    fun whenRuleContainsDotStarThenDotIsLiteralAndStarIsWildcard() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/path/.*js", domains = listOf("example.com")))

        assertTrue(testee.containedInBlocklist("https://example.com", "https://testing.com/path/.script.js"))
        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/path/script.js"))
        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/path/sscript.js"))
    }

    @Test
    fun whenRuleContainsBackslashStarThenItDoesNotEscapeWildcard() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/path/\\*", domains = listOf("example.com")))

        // \* does NOT escape the wildcard — \ is literal and * is still a wildcard
        // so it should NOT match a literal * in the URL
        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/path/*"))
    }

    @Test
    fun whenFirstRuleDomainDoesNotMatchButSecondRuleDomainDoesThenReturnTrue() {
        val settings = """
            {
                "blockedRequests": {
                    "testing.com": {
                        "rules": [
                            {"rule": "testing.com/*.jpg", "domains": ["other.com"], "reason": "breakage"},
                            {"rule": "testing.com/*.jpg", "domains": ["example.com"], "reason": "breakage"}
                        ]
                    }
                }
            }
        """.trimIndent()
        val testee = createTestee(settings)

        assertTrue(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenRuleMissingReasonButHasOtherRequiredFieldsThenRuleStillApplies() {
        val settings = """
            {
                "blockedRequests": {
                    "testing.com": {
                        "rules": [
                            {"rule": "testing.com/*.jpg", "domains": ["example.com"]}
                        ]
                    }
                }
            }
        """.trimIndent()
        val testee = createTestee(settings)

        assertTrue(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenRuleHasUnknownPropertyButOtherRulesInSameEntryAreValidThenValidRulesStillApply() {
        val settings = """
            {
                "blockedRequests": {
                    "testing.com": {
                        "rules": [
                            {"rule": "testing.com/*.png", "domains": ["example.com"], "reason": "breakage", "unknownField": true},
                            {"rule": "testing.com/*.jpg", "domains": ["example.com"], "reason": "breakage"}
                        ]
                    }
                }
            }
        """.trimIndent()
        val testee = createTestee(settings)

        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/image.png"))
        assertTrue(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenRuleHasDifferentCaseThanRequestThenCaseSensitiveMatchFails() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/IMAGE.js", domains = listOf("example.com")))

        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/image.js"))
    }

    @Test
    fun whenRuleAndRequestHaveSameCaseThenReturnTrue() {
        val testee = createTestee(settingsWithRule(entry = "testing.com", rule = "testing.com/IMAGE.js", domains = listOf("example.com")))

        assertTrue(testee.containedInBlocklist("https://example.com", "https://testing.com/IMAGE.js"))
    }

    @Test
    fun whenDocumentDomainIsInExceptionsThenReturnFalse() {
        val testee = createTestee(
            settings = settingsWithRule(entry = "testing.com", rule = "testing.com/*.jpg", domains = listOf("<all>")),
            exceptions = listOf(FeatureException("example.com", "test exception")),
        )

        assertFalse(testee.containedInBlocklist("https://example.com", "https://testing.com/image.jpg"))
    }

    @Test
    fun whenDocumentSubdomainMatchesExceptionThenReturnFalse() {
        val testee = createTestee(
            settings = settingsWithRule(entry = "testing.com", rule = "testing.com/*.jpg", domains = listOf("<all>")),
            exceptions = listOf(FeatureException("example.com", "test exception")),
        )

        assertFalse(testee.containedInBlocklist("https://sub.example.com", "https://testing.com/image.jpg"))
    }

    private fun settingsWithRule(
        entry: String,
        rule: String,
        domains: List<String>,
    ): String {
        val domainsJson = domains.joinToString(",") { "\"$it\"" }
        return """
            {
                "blockedRequests": {
                    "$entry": {
                        "rules": [
                            {"rule": "$rule", "domains": [$domainsJson], "reason": "site breakage"}
                        ]
                    }
                }
            }
        """.trimIndent()
    }
}
