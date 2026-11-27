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

package com.duckduckgo.privacy.config.impl.features.amplinks

import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.AmpLinkType
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.amplinks.AmpLinksRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList

class RealAmpLinksTest {

    private lateinit var testee: RealAmpLinks
    private val mockAmpLinksRepository: AmpLinksRepository = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()

    private val ampKeywords = CopyOnWriteArrayList(
        listOf("=amp", "amp=", "&amp", "amp&", "/amp", "amp/", ".amp", "amp.", "%amp", "amp%", "_amp", "amp_", "?amp"),
    )

    @Before
    fun setup() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.AmpLinksFeatureName.value)).thenReturn(true)
        whenever(mockAmpLinksRepository.exceptions).thenReturn(CopyOnWriteArrayList())
        whenever(mockAmpLinksRepository.ampLinkFormats).thenReturn(CopyOnWriteArrayList())
        whenever(mockAmpLinksRepository.ampKeywords).thenReturn(ampKeywords)
        testee = RealAmpLinks(mockAmpLinksRepository, mockFeatureToggle, mockUnprotectedTemporary, mockUserAllowListRepository)
    }

    @Test
    fun whenIsExceptionCalledAndDomainIsInUserAllowListThenReturnTrue() {
        whenever(mockUserAllowListRepository.isUrlInUserAllowList(anyString())).thenReturn(true)
        assertTrue(testee.isAnException("test.com"))
    }

    @Test
    fun whenProcessDestinationUrlAndExtractedUrlIsNullThenReturnInitialUrl() {
        val destinationUrl = testee.processDestinationUrl("https://example.com", null)
        assertEquals("https://example.com", destinationUrl)
    }

    @Test
    fun whenProcessDestinationUrlAndExtractedUrlIsAnExceptionThenReturnInitialUrl() {
        whenever(mockUserAllowListRepository.isUrlInUserAllowList(anyString())).thenReturn(true)
        val destinationUrl = testee.processDestinationUrl("https://example.com", "https://foo.com")
        assertEquals("https://example.com", destinationUrl)
    }

    @Test
    fun whenProcessDestinationUrlAndExtractedUrlDoesNotStartWithHttpOrHttpsThenReturnInitialUrl() {
        val destinationUrl = testee.processDestinationUrl("https://example.com", "foo.com")
        assertEquals("https://example.com", destinationUrl)
    }

    @Test
    fun whenProcessDestinationUrlAndExtractedUrlStartsWithHttpThenReturnExtractedUrl() {
        val destinationUrl = testee.processDestinationUrl("https://example.com", "http://foo.com")
        assertEquals("http://foo.com", destinationUrl)
    }

    @Test
    fun whenProcessDestinationUrlAndExtractedUrlStartsWithHttpsThenReturnExtractedUrl() {
        val destinationUrl = testee.processDestinationUrl("https://example.com", "https://foo.com")
        assertEquals("https://foo.com", destinationUrl)
    }

    @Test
    fun whenKeywordIsPartOfAnotherWordInTheQueryThenUrlIsNotTreatedAsAmpLink() {
        val url = "https://example.com/authorize?client_id=abc&scope=openid" +
            "&consent=isGpcEnabled%3D0%26datestamp%3DMon%2BNov%2B24%26version%3D1"

        assertNull(testee.extractCanonicalFromAmpLink(url))
    }

    @Test
    fun whenAmpKeywordIsInTheQueryAtAWordBoundaryThenUrlIsTreatedAsCloakedAmpLink() {
        val url = "https://example.com/article.html?amp"

        assertTrue(testee.extractCanonicalFromAmpLink(url) is AmpLinkType.CloakedAmpLink)
    }

    @Test
    fun whenKeywordAppearsMidWordBeforeAtABoundaryThenUrlIsTreatedAsCloakedAmpLink() {
        val url = "https://example.com/camp/page-amp/"

        assertTrue(testee.extractCanonicalFromAmpLink(url) is AmpLinkType.CloakedAmpLink)
    }

    @Test
    fun whenKeywordIsPartOfAnotherWordInThePathThenUrlIsNotTreatedAsAmpLink() {
        val url = "https://example.com/camp/stamp/"

        assertNull(testee.extractCanonicalFromAmpLink(url))
    }

    @Test
    fun whenKeywordListContainsEmptyKeywordThenPlainUrlIsNotTreatedAsAmpLink() {
        ampKeywords.add(0, "")

        assertNull(testee.extractCanonicalFromAmpLink("https://example.com/page"))
    }
}
