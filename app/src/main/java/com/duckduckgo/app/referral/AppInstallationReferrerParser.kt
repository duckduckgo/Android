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
import com.duckduckgo.app.referral.ParsedReferrerResult.EuAuctionBrowserChoiceReferrerFound
import com.duckduckgo.app.referral.ParsedReferrerResult.EuAuctionSearchChoiceReferrerFound
import com.duckduckgo.app.referral.ParsedReferrerResult.ReferrerNotFound
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.logcat

interface AppInstallationReferrerParser {

    fun parse(referrer: String): ParsedReferrerResult
}

@Suppress("SameParameterValue")
@ContributesBinding(AppScope::class)
class QueryParamReferrerParser @Inject constructor(
    private val originAttributeHandler: ReferrerOriginAttributeHandler,
) : AppInstallationReferrerParser {

    override fun parse(referrer: String): ParsedReferrerResult {
        logcat(VERBOSE) { "Full referrer string: $referrer" }

        val referrerParts = splitIntoConstituentParts(referrer)
        if (referrerParts.isEmpty()) return ReferrerNotFound(fromCache = false)

        // processing this doesn't change anything with the ATB-based campaign referrer or EU search/ballot logic
        originAttributeHandler.process(referrerParts)

        val auctionReferrer = extractEuAuctionReferrer(referrerParts)
        if (auctionReferrer is EuAuctionSearchChoiceReferrerFound || auctionReferrer is EuAuctionBrowserChoiceReferrerFound) {
            return auctionReferrer
        }

        return extractCampaignReferrer(referrerParts)
    }

    private fun extractEuAuctionReferrer(referrerParts: List<String>): ParsedReferrerResult {
        logcat { "Looking for Google EU Auction referrer data" }
        for (part in referrerParts) {
            logcat(VERBOSE) { "Analysing query param part: $part" }
            if (part.startsWith(INSTALLATION_SOURCE_KEY) && part.endsWith(INSTALLATION_SEARCH_CHOICE_SOURCE_EU_AUCTION_VALUE)) {
                logcat(INFO) { "App installed as a result of the EU auction - Search Choice" }
                return EuAuctionSearchChoiceReferrerFound()
            }
            if (part.startsWith(INSTALLATION_SOURCE_KEY) && part.endsWith(INSTALLATION_BROWSER_CHOICE_SOURCE_EU_AUCTION_VALUE)) {
                logcat(INFO) { "App installed as a result of the EU auction - Browser Choice" }
                return EuAuctionBrowserChoiceReferrerFound()
            }
        }

        logcat { "No EU referrer data found; app not installed as a result of EU auction or choice screen" }
        return ReferrerNotFound()
    }

    private fun extractCampaignReferrer(referrerParts: List<String>): ParsedReferrerResult {
        logcat { "Looking for regular referrer data" }
        for (part in referrerParts) {
            logcat(VERBOSE) { "Analysing query param part: $part" }
            if (part.contains(CAMPAIGN_NAME_PREFIX)) {
                return extractCampaignNameSuffix(part, CAMPAIGN_NAME_PREFIX)
            }
        }

        logcat { "Referrer information does not contain inspected campaign names" }
        return ReferrerNotFound()
    }

    private fun extractCampaignNameSuffix(
        part: String,
        prefix: String,
    ): ParsedReferrerResult {
        logcat(INFO) { "Found target campaign name prefix $prefix in $part" }
        val suffix = stripCampaignName(part, prefix)

        if (suffix.length < 2) {
            logcat(WARN) { "Unexpected suffix length for campaign" }
            return ReferrerNotFound(fromCache = false)
        }

        val condensedSuffix = suffix.take(2)
        logcat(INFO) { "Found suffix $condensedSuffix (looking for $prefix, found in $part)" }
        return CampaignReferrerFound(condensedSuffix)
    }

    private fun stripCampaignName(
        fullCampaignName: String,
        prefix: String,
    ): String {
        return fullCampaignName.substringAfter(prefix, "")
    }

    private fun splitIntoConstituentParts(referrer: String?): List<String> {
        return referrer?.split("&") ?: emptyList()
    }

    companion object {
        private const val CAMPAIGN_NAME_PREFIX = "DDGRA"

        private const val INSTALLATION_SOURCE_KEY = "utm_source"
        private const val INSTALLATION_SEARCH_CHOICE_SOURCE_EU_AUCTION_VALUE = "eea-search-choice"
        private const val INSTALLATION_BROWSER_CHOICE_SOURCE_EU_AUCTION_VALUE = "eea-browser-choice"
    }
}

sealed class ParsedReferrerResult(open val fromCache: Boolean = false) {
    data class EuAuctionSearchChoiceReferrerFound(override val fromCache: Boolean = false) : ParsedReferrerResult(fromCache)
    data class EuAuctionBrowserChoiceReferrerFound(override val fromCache: Boolean = false) : ParsedReferrerResult(fromCache)
    data class CampaignReferrerFound(
        val campaignSuffix: String,
        override val fromCache: Boolean = false,
    ) : ParsedReferrerResult(fromCache)

    data class ReferrerNotFound(override val fromCache: Boolean = false) : ParsedReferrerResult(fromCache)
    data class ParseFailure(val reason: ParseFailureReason) : ParsedReferrerResult()
    data object ReferrerInitialising : ParsedReferrerResult()
}

sealed class ParseFailureReason {
    data object FeatureNotSupported : ParseFailureReason()
    data object ServiceUnavailable : ParseFailureReason()
    data object DeveloperError : ParseFailureReason()
    data object ServiceDisconnected : ParseFailureReason()
    data object UnknownError : ParseFailureReason()
    data object ReferralServiceUnavailable : ParseFailureReason()
}
