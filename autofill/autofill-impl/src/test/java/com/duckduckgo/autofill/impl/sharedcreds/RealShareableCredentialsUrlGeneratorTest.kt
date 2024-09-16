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

package com.duckduckgo.autofill.impl.sharedcreds

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.encoding.TestUrlUnicodeNormalizer
import com.duckduckgo.autofill.impl.sharedcreds.SharedCredentialsParser.OmnidirectionalRule
import com.duckduckgo.autofill.impl.sharedcreds.SharedCredentialsParser.SharedCredentialConfig
import com.duckduckgo.autofill.impl.sharedcreds.SharedCredentialsParser.UnidirectionalRule
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher.ExtractedUrlParts
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealShareableCredentialsUrlGeneratorTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val jsonParser: SharedCredentialsParser = mock()

    private val testee = RealShareableCredentialsUrlGenerator(
        autofillUrlMatcher = AutofillDomainNameUrlMatcher(TestUrlUnicodeNormalizer()),
    )

    @Before
    fun before() {
        runTest {
            whenever(jsonParser.read()).thenReturn(SharedCredentialConfig(omnidirectionalRules = emptyList(), unidirectionalRules = emptyList()))
        }
    }

    @Test
    fun whenSiteInOmnidirectionalListThenAllRelatedSitesReturned() = runTest {
        val config = config(
            omnidirectionalRules = listOf(
                omnidirectionalRule(listOf("foo.com", "example.com", "bar.com")),
                omnidirectionalRule(listOf("unrelated.com")),
            ),
        )

        val result = testee.generateShareableUrls("example.com", config)

        result.assertMatches(
            listOf(
                "foo.com",
                "bar.com",
            ),
        )
    }

    @Test
    fun whenSiteInOmnidirectionalListMultipleTimesThenOnlyReturnedOnce() = runTest {
        val config = config(
            omnidirectionalRules = listOf(
                omnidirectionalRule(listOf("foo.com", "example.com", "bar.com")),
                omnidirectionalRule(listOf("foo.com", "example.com", "bar.com")),
            ),
        )

        val result = testee.generateShareableUrls("example.com", config)

        result.assertMatches(
            listOf(
                "foo.com",
                "bar.com",
            ),
        )
    }

    @Test
    fun whenSiteInUnidirectionalToRuleThenOfferedFromUrl() = runTest {
        val config = config(
            unidirectionalRules = listOf(
                unidirectionalRule(
                    from = listOf("old.com"),
                    to = listOf("new.com"),
                ),
            ),
        )
        val result = testee.generateShareableUrls("new.com", config)
        result.assertMatches(listOf("old.com"))
    }

    @Test
    fun whenSiteInUnidirectionalFromRuleThenNotOfferedToUrl() = runTest {
        val config = config(
            unidirectionalRules = listOf(
                unidirectionalRule(
                    from = listOf("old.com"),
                    to = listOf("new.com"),
                ),
            ),
        )
        val result = testee.generateShareableUrls("old.com", config)
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenSiteInMultipleUnidirectionalListThenReturnedOnce() = runTest {
        val config = config(
            unidirectionalRules = listOf(
                unidirectionalRule(
                    from = listOf("old.com"),
                    to = listOf("new.com"),
                ),
                unidirectionalRule(
                    from = listOf("old.com"),
                    to = listOf("new.com"),
                ),
            ),
        )
        val result = testee.generateShareableUrls("new.com", config)

        result.assertMatches(listOf("old.com"))
    }

    @Test
    fun whenFromUnidirectionalListHasMultipleSitesThenAllReturned() = runTest {
        val config = config(
            unidirectionalRules = listOf(
                unidirectionalRule(
                    from = listOf("unrelated.com"),
                    to = listOf("not-expected.com"),
                ),
                unidirectionalRule(
                    from = listOf("expected.com", "also-expected.com", "and-another.com"),
                    to = listOf("example.com"),
                ),
                unidirectionalRule(
                    from = listOf("expected-from-another-list.com"),
                    to = listOf("example.com"),
                ),
            ),
        )
        val result = testee.generateShareableUrls("example.com", config)

        result.assertMatches(
            listOf(
                "expected-from-another-list.com",
                "expected.com",
                "and-another.com",
                "also-expected.com",
            ),
        )
    }

    @Test
    fun whenMatchesFromOmnidirectionalAndUnidirectionalThenReturnedOnce() = runTest {
        val config = config(
            omnidirectionalRules = listOf(
                omnidirectionalRule(listOf("example.com", "expected.com")),
            ),
            unidirectionalRules = listOf(
                unidirectionalRule(
                    from = listOf("example.com"),
                    to = listOf("expected.com"),
                ),
            ),
        )
        val result = testee.generateShareableUrls("example.com", config)
        result.assertMatches(listOf("expected.com"))
    }

    @Test
    fun whenFullUncleanedUrlGivenThenStillMatches() = runTest {
        val config = config(omnidirectionalRules = listOf(omnidirectionalRule(listOf("example.com", "expected.com"))))
        val result = testee.generateShareableUrls("https://example.com/hello/world", config)
        result.assertMatches(listOf("expected.com"))
    }

    @Test
    fun whenConfigIsEmptyThenNoSitesReturned() = runTest {
        val config = emptyLists()
        val result = testee.generateShareableUrls("example.com", config)
        result.assertMatches(emptyList())
    }

    private fun emptyLists(): SharedCredentialConfig {
        return config()
    }

    private fun config(
        omnidirectionalRules: List<OmnidirectionalRule> = emptyList(),
        unidirectionalRules: List<UnidirectionalRule> = emptyList(),
    ): SharedCredentialConfig {
        return SharedCredentialConfig(
            omnidirectionalRules = omnidirectionalRules,
            unidirectionalRules = unidirectionalRules,
        )
    }

    private fun omnidirectionalRule(
        shared: List<String>,
    ): OmnidirectionalRule {
        return OmnidirectionalRule(
            shared = shared.toUrlParts(),
        )
    }

    private fun unidirectionalRule(
        from: List<String>,
        to: List<String>,
        fromDomainsAreObsoleted: Boolean? = null,
    ): UnidirectionalRule {
        return UnidirectionalRule(
            from = from.toUrlParts(),
            to = to.toUrlParts(),
            fromDomainsAreObsoleted = fromDomainsAreObsoleted,
        )
    }
}

private fun List<String>.toUrlParts(): List<ExtractedUrlParts> {
    return this.map { ExtractedUrlParts(it, it, null, 443) }
}

private fun List<ExtractedUrlParts>.assertMatches(expected: List<String>) {
    assertEquals("Lists are different sizes", expected.size, this.size)
    assertEquals(expected.toUrlParts().toHashSet(), this.toHashSet())
}
