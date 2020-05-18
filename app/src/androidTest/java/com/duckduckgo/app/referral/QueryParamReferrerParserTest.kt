/*
 * Copyright (c) 2019 DuckDuckGo
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
import com.duckduckgo.app.referral.ParsedReferrerResult.EuAuctionReferrerFound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryParamReferrerParserTest {

    private val testee: QueryParamReferrerParser = QueryParamReferrerParser()

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
    fun whenReferrerContainsTargetWithDifferentCaseThenNoReferrerFound() {
        verifyReferrerNotFound(testee.parse("ddgraAB"))
    }

    @Test
    fun whenReferrerContainsEuAuctionDataThenEuActionReferrerFound() {
        val result = testee.parse("$INSTALLATION_SOURCE_KEY=$INSTALLATION_SOURCE_EU_AUCTION_VALUE")
        assertTrue(result is EuAuctionReferrerFound)
    }

    @Test
    fun whenReferrerContainsBothEuAuctionAndCampaignReferrerDataThenEuActionReferrerFound() {
        val result = testee.parse("key1=DDGRAAB&key2=foo&key3=bar&$INSTALLATION_SOURCE_KEY=$INSTALLATION_SOURCE_EU_AUCTION_VALUE")
        assertTrue(result is EuAuctionReferrerFound)
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

    private fun verifyCampaignReferrerFound(expectedReferrer: String, result: ParsedReferrerResult) {
        assertTrue(result is CampaignReferrerFound)
        val value = (result as CampaignReferrerFound).campaignSuffix
        assertEquals(expectedReferrer, value)
    }

    private fun verifyReferrerNotFound(result: ParsedReferrerResult) {
        assertTrue(result is ParsedReferrerResult.ReferrerNotFound)
    }

    companion object {
        private const val INSTALLATION_SOURCE_KEY = "utm_source"
        private const val INSTALLATION_SOURCE_EU_AUCTION_VALUE = "eea-search-choice"
    }

}
