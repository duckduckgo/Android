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

package com.duckduckgo.referral.impl

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.referral.api.ParsedReferrerResult
import com.duckduckgo.referral.api.ParsedReferrerResult.CampaignReferrerFound
import com.duckduckgo.referral.api.ParsedReferrerResult.EuAuctionBrowserChoiceReferrerFound
import com.duckduckgo.referral.api.ParsedReferrerResult.EuAuctionSearchChoiceReferrerFound
import com.duckduckgo.referral.api.ReferrerParserPlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

private fun pluginPoint(vararg plugins: ReferrerParserPlugin) = object : PluginPoint<ReferrerParserPlugin> {
    override fun getPlugins(): Collection<ReferrerParserPlugin> = plugins.toList()
}

class QueryParamReferrerParserTest {

    private val testee: QueryParamReferrerParser = QueryParamReferrerParser(
        referrerParserPlugins = pluginPoint(),
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
        val result = testee.parse("DDGRA")
        verifyReferrerNotFound(result)
    }

    @Test
    fun whenReferrerIsParsedThenPluginsAreInvokedWithParsedParams() {
        val plugin: ReferrerParserPlugin = mock()
        val testeeWithPlugin = QueryParamReferrerParser(pluginPoint(plugin))
        testeeWithPlugin.parse("key1=val1&key2=val2")
        verify(plugin).process(mapOf("key1" to "val1", "key2" to "val2"))
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
    fun whenReferrerDoesNotContainEuAuctionDataThenPluginCalled() {
        val plugin: ReferrerParserPlugin = mock()
        val testeeWithPlugin = QueryParamReferrerParser(pluginPoint(plugin))
        testeeWithPlugin.parse("origin=funnel_playstore_whatever")
        verify(plugin).process(any())
    }

    @Test
    fun whenReferrerDoesContainEuAuctionDataThenPluginStillCalled() {
        val plugin: ReferrerParserPlugin = mock()
        val testeeWithPlugin = QueryParamReferrerParser(pluginPoint(plugin))
        val result = testeeWithPlugin.parse("$INSTALLATION_SOURCE_KEY=$INSTALLATION_SOURCE_EU_BROWSER_CHOICE_AUCTION_VALUE")
        verify(plugin).process(any())
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
