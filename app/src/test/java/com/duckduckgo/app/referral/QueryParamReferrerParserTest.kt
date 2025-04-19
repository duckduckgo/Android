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

package com.duckduckgo.app.referral

import com.duckduckgo.app.referral.ParsedReferrerResult.CampaignReferrerFound
import com.duckduckgo.app.referral.ParsedReferrerResult.EuAuctionBrowserChoiceReferrerFound
import com.duckduckgo.app.referral.ParsedReferrerResult.EuAuctionSearchChoiceReferrerFound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class QueryParamReferrerParserTest {

    private val originAttributeHandler: ReferrerOriginAttributeHandler = mock()

    private val testee: QueryParamReferrerParser = QueryParamReferrerParser(
        originAttributeHandler = originAttributeHandler,
    )

    @Test
    fun whenReferrerDoesNotContainTargetThenNoReferrerFound() {
        verifyReferrerNotFound(testee.parse("ABC"))
    }

    @Test
    fun whenReferrerContainsTargetAndLongSuffixThenShortenedReferrerFound() {
        val result = testee.parse("DDGRAABC")
        verifyCampaignReferrerFound("AB", result)
    }

    @Test
    fun whenReferrerContainsTargetAndTwoCharSuffixThenReferrerFound() {
        val result = testee.parse("DDGRAXY")
        verifyCampaignReferrerFound("XY", result)
    }

    @Test
    fun whenReferrerContainsTargetAndOneCharSuffixThenNoReferrerFound() {
        val result = testee.parse("DDGRAX")
        verifyReferrerNotFound(result)
    }

    @Test
    fun whenReferrerContainsTargetButNoSuffixThenNoReferrerFound() {
        val result = testee.parse("DDGRAX")
        verifyReferrerNotFound(result)
    }

    @Test
    fun whenReferrerIsEmptyThenNoReferrerFound() {
        verifyReferrerNotFound(testee.parse(""))
    }

    @Test
    fun whenReferrerContainsTargetAsFirstParamThenReferrerFound() {
        val result = testee.parse("key1=DDGRAAB&key2=foo&key3=bar")
        verifyCampaignReferrerFound("AB", result)
    }

    @Test
    fun whenReferrerContainsTargetAsLastParamThenReferrerFound() {
        val result = testee.parse("key1=foo&key2=bar&key3=DDGRAAB")
        verifyCampaignReferrerFound("AB", result)
    }

    @Test
    fun whenReferrerContainsTargetAndUtmCampaignThenReferrerFound() {
        val result = testee.parse("key1=foo&key2=bar&key3=DDGRAAB&origin=funnel_playstore_whatever")
        verifyCampaignReferrerFound("AB", result)
    }

    @Test
    fun whenReferrerContainsTargetWithDifferentCaseThenNoReferrerFound() {
        verifyReferrerNotFound(testee.parse("ddgraAB"))
    }

    @Test
    fun whenReferrerContainsEuAuctionSearchChoiceDataThenEuActionReferrerFound() {
        val result = testee.parse("$INSTALLATION_SOURCE_KEY=$INSTALLATION_SOURCE_EU_SEARCH_CHOICE_AUCTION_VALUE")
        assertTrue(result is EuAuctionSearchChoiceReferrerFound)
    }

    @Test
    fun whenReferrerContainsBothEuAuctionSearchChoiceAndCampaignReferrerDataThenEuActionReferrerFound() {
        val result = testee.parse("key1=DDGRAAB&key2=foo&key3=bar&$INSTALLATION_SOURCE_KEY=$INSTALLATION_SOURCE_EU_SEARCH_CHOICE_AUCTION_VALUE")
        assertTrue(result is EuAuctionSearchChoiceReferrerFound)
    }

    @Test
    fun whenReferrerContainsInstallationSourceKeyButNotMatchingValueThenNoReferrerFound() {
        val result = testee.parse("$INSTALLATION_SOURCE_KEY=bar")
        verifyReferrerNotFound(result)
    }

    @Test
    fun whenReferrerContainsInstallationSourceKeyAndNoEuAuctionValueButHasCampaignReferrerDataThenCampaignReferrerFound() {
        val result = testee.parse("key1=DDGRAAB&key2=foo&key3=bar&$INSTALLATION_SOURCE_KEY=bar")
        verifyCampaignReferrerFound("AB", result)
    }

    @Test
    fun whenReferrerContainsEuAuctionBrowserChoiceDataThenEuActionReferrerFound() {
        val result = testee.parse("$INSTALLATION_SOURCE_KEY=$INSTALLATION_SOURCE_EU_BROWSER_CHOICE_AUCTION_VALUE")
        assertTrue(result is EuAuctionBrowserChoiceReferrerFound)
    }

    @Test
    fun whenReferrerDoesNotContainEuAuctionDataThenUtmCampaignProcessorCalled() {
        testee.parse("origin=funnel_playstore_whatever")
        verify(originAttributeHandler).process(any())
    }

    @Test
    fun whenReferrerDoesContainEuAuctionDataThenUtmCampaignProcessorStillCalled() {
        val result = testee.parse("$INSTALLATION_SOURCE_KEY=$INSTALLATION_SOURCE_EU_BROWSER_CHOICE_AUCTION_VALUE")
        verify(originAttributeHandler).process(any())
        assertTrue(result is EuAuctionBrowserChoiceReferrerFound)
    }

    @Test
    fun whenReferrerContainsBothEuAuctionBrowserChoiceAndCampaignReferrerDataThenEuActionReferrerFound() {
        val result = testee.parse("key1=DDGRAAB&key2=foo&key3=bar&$INSTALLATION_SOURCE_KEY=$INSTALLATION_SOURCE_EU_BROWSER_CHOICE_AUCTION_VALUE")
        assertTrue(result is EuAuctionBrowserChoiceReferrerFound)
    }

    private fun verifyCampaignReferrerFound(
        expectedReferrer: String,
        result: ParsedReferrerResult,
    ) {
        assertTrue(result is CampaignReferrerFound)
        val value = (result as CampaignReferrerFound).campaignSuffix
        assertEquals(expectedReferrer, value)
    }

    private fun verifyReferrerNotFound(result: ParsedReferrerResult) {
        assertTrue(result is ParsedReferrerResult.ReferrerNotFound)
    }

    companion object {
        private const val INSTALLATION_SOURCE_KEY = "utm_source"
        private const val INSTALLATION_SOURCE_EU_SEARCH_CHOICE_AUCTION_VALUE = "eea-search-choice"
        private const val INSTALLATION_SOURCE_EU_BROWSER_CHOICE_AUCTION_VALUE = "eea-browser-choice"
    }
}
