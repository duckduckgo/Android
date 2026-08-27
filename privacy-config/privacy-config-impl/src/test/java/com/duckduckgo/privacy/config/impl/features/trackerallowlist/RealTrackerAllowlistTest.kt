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

package com.duckduckgo.privacy.config.impl.features.trackerallowlist

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.store.AllowlistRuleEntity
import com.duckduckgo.privacy.config.store.TrackerAllowlistEntity
import com.duckduckgo.privacy.config.store.features.trackerallowlist.TrackerAllowlistRepository
import com.duckduckgo.privacy.config.store.features.trackerallowlist.buildRulesByDomain
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealTrackerAllowlistTest {

    private val mockTrackerAllowlistRepository: TrackerAllowlistRepository = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private lateinit var testee: RealTrackerAllowlist

    @Before
    fun setup() {
        whenever(mockFeatureToggle.isFeatureEnabled(any(), any())).thenReturn(true)

        testee = RealTrackerAllowlist(mockTrackerAllowlistRepository, mockFeatureToggle)
    }

    @Test
    fun whenUrlCannotBeParsedThenDoNotThrowAnException() {
        val url = "://allowlist-tracker-1.com:5000/videos.js"
        givenDomainIsAnException("allowlist-tracker-1.com")

        assertFalse(testee.isAnException("test.com", url))
    }

    @Test
    fun whenRuleMatchesThenReturnsTrue() {
        givenAllowlistContains(
            TrackerAllowlistEntity(
                domain = "tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker.com/videos.js", domains = listOf("site.com"), reason = ""),
                ),
            ),
        )

        assertTrue(testee.isAnException("https://site.com", "https://tracker.com/videos.js"))
    }

    @Test
    fun whenSubdomainRequestThenReturnsTrue() {
        givenAllowlistContains(
            TrackerAllowlistEntity(
                domain = "tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker.com/videos.js", domains = listOf("<all>"), reason = ""),
                ),
            ),
        )

        assertTrue(testee.isAnException("https://site.com", "https://a.b.tracker.com/videos.js"))
    }

    @Test
    fun whenDocumentDomainDoesNotMatchThenReturnsFalse() {
        givenAllowlistContains(
            TrackerAllowlistEntity(
                domain = "tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker.com/videos.js", domains = listOf("site.com"), reason = ""),
                ),
            ),
        )

        assertFalse(testee.isAnException("https://other.com", "https://tracker.com/videos.js"))
    }

    @Test
    fun whenDomainNotInListThenReturnsFalse() {
        givenAllowlistContains(
            TrackerAllowlistEntity(
                domain = "tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker.com/videos.js", domains = listOf("<all>"), reason = ""),
                ),
            ),
        )

        assertFalse(testee.isAnException("https://site.com", "https://other-tracker.com/videos.js"))
    }

    @Test
    fun whenFeatureDisabledThenReturnsFalse() {
        whenever(mockFeatureToggle.isFeatureEnabled(any(), any())).thenReturn(false)
        givenAllowlistContains(
            TrackerAllowlistEntity(
                domain = "tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker.com/videos.js", domains = listOf("<all>"), reason = ""),
                ),
            ),
        )

        assertFalse(testee.isAnException("https://site.com", "https://tracker.com/videos.js"))
    }

    @Test
    fun whenRepositorySnapshotChangesThenReadsLatest() {
        givenAllowlistContains(
            TrackerAllowlistEntity(
                domain = "tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker.com/videos.js", domains = listOf("<all>"), reason = ""),
                ),
            ),
        )
        assertTrue(testee.isAnException("https://site.com", "https://tracker.com/videos.js"))
        assertFalse(testee.isAnException("https://site.com", "https://tracker-2.com/videos.js"))

        givenAllowlistContains(
            TrackerAllowlistEntity(
                domain = "tracker-2.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker-2.com/videos.js", domains = listOf("<all>"), reason = ""),
                ),
            ),
        )
        assertFalse(testee.isAnException("https://site.com", "https://tracker.com/videos.js"))
        assertTrue(testee.isAnException("https://site.com", "https://tracker-2.com/videos.js"))
    }

    @Test
    fun whenRuleRegexIsInvalidThenRuleIsSkipped() {
        givenAllowlistContains(
            TrackerAllowlistEntity(
                domain = "tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker.com/[invalid", domains = listOf("<all>"), reason = ""),
                    AllowlistRuleEntity(rule = "tracker.com/good.js", domains = listOf("<all>"), reason = ""),
                ),
            ),
        )

        assertTrue(testee.isAnException("https://site.com", "https://tracker.com/good.js"))
        assertFalse(testee.isAnException("https://site.com", "https://tracker.com/bad.js"))
    }

    @Test
    fun whenNoEntryForSubdomainThenParentEntryRulesApply() {
        givenAllowlistContains(
            TrackerAllowlistEntity(
                domain = "tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker.com/videos.js", domains = listOf("<all>"), reason = ""),
                ),
            ),
        )

        assertTrue(testee.isAnException("https://site.com", "https://cdn.tracker.com/videos.js"))
    }

    @Test
    fun whenSubdomainHasItsOwnEntryThenOnlyItsRulesApply() {
        givenAllowlistContains(
            TrackerAllowlistEntity(
                domain = "tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker.com/videos.js", domains = listOf("<all>"), reason = ""),
                ),
            ),
            TrackerAllowlistEntity(
                domain = "cdn.tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "cdn.tracker.com/images.js", domains = listOf("<all>"), reason = ""),
                ),
            ),
        )

        assertTrue(testee.isAnException("https://site.com", "https://cdn.tracker.com/images.js"))
        assertFalse(testee.isAnException("https://site.com", "https://cdn.tracker.com/videos.js"))
        assertTrue(testee.isAnException("https://site.com", "https://tracker.com/videos.js"))
    }

    @Test
    fun whenSubdomainEntryHasNoRulesThenParentEntryRulesDoNotApply() {
        givenAllowlistContains(
            TrackerAllowlistEntity(
                domain = "tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker.com/videos.js", domains = listOf("<all>"), reason = ""),
                ),
            ),
            TrackerAllowlistEntity(domain = "cdn.tracker.com", rules = emptyList()),
        )

        assertFalse(testee.isAnException("https://site.com", "https://cdn.tracker.com/videos.js"))
    }

    @Test
    fun whenEntriesShareNormalizedDomainThenRulesFromBothApply() {
        givenAllowlistContains(
            TrackerAllowlistEntity(
                domain = "tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker.com/videos.js", domains = listOf("<all>"), reason = ""),
                ),
            ),
            TrackerAllowlistEntity(
                domain = "www.tracker.com",
                rules = listOf(
                    AllowlistRuleEntity(rule = "tracker.com/images.js", domains = listOf("<all>"), reason = ""),
                ),
            ),
        )

        assertTrue(testee.isAnException("https://site.com", "https://tracker.com/videos.js"))
        assertTrue(testee.isAnException("https://site.com", "https://tracker.com/images.js"))
    }

    private fun givenDomainIsAnException(domain: String) {
        givenAllowlistContains(TrackerAllowlistEntity(domain, emptyList()))
    }

    private fun givenAllowlistContains(vararg entities: TrackerAllowlistEntity) {
        val list = entities.toList()
        whenever(mockTrackerAllowlistRepository.rulesByDomain).thenReturn(buildRulesByDomain(list))
    }
}
