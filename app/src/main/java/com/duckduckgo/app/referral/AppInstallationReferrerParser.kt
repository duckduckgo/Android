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
import com.duckduckgo.app.referral.ParsedReferrerResult.ReferrerNotFound
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import timber.log.Timber
import javax.inject.Inject

interface AppInstallationReferrerParser {

    fun parse(referrer: String): ParsedReferrerResult
}

@Suppress("SameParameterValue")
@ContributesBinding(AppScope::class)
class QueryParamReferrerParser @Inject constructor() : AppInstallationReferrerParser {

    override fun parse(referrer: String): ParsedReferrerResult {
        val referrerParts = splitIntoConstituentParts(referrer)
        if (referrerParts.isNullOrEmpty()) return ReferrerNotFound(fromCache = false)

        val auctionReferrer = extractEuAuctionReferrer(referrerParts)
        if (auctionReferrer is EuAuctionReferrerFound) {
            return auctionReferrer
        }

        return extractCampaignReferrer(referrerParts)
    }

    private fun extractEuAuctionReferrer(referrerParts: List<String>): ParsedReferrerResult {
        Timber.d("Looking for Google EU Auction referrer data")
        for (part in referrerParts) {

            Timber.v("Analysing query param part: $part")
            if (part.startsWith(INSTALLATION_SOURCE_KEY) && part.endsWith(INSTALLATION_SOURCE_EU_AUCTION_VALUE)) {
                Timber.i("App installed as a result of the EU auction")
                return EuAuctionReferrerFound()
            }
        }

        Timber.d("App not installed as a result of EU auction")
        return ReferrerNotFound()
    }

    private fun extractCampaignReferrer(referrerParts: List<String>): ParsedReferrerResult {
        Timber.d("Looking for regular referrer data")
        for (part in referrerParts) {

            Timber.v("Analysing query param part: $part")
            if (part.contains(CAMPAIGN_NAME_PREFIX)) {
                return extractCampaignNameSuffix(part, CAMPAIGN_NAME_PREFIX)
            }
        }

        Timber.d("Referrer information does not contain inspected campaign names")
        return ReferrerNotFound()
    }

    private fun extractCampaignNameSuffix(
        part: String,
        prefix: String
    ): ParsedReferrerResult {
        Timber.i("Found target campaign name prefix $prefix in $part")
        val suffix = stripCampaignName(part, prefix)

        if (suffix.length < 2) {
            Timber.w("Unexpected suffix length for campaign")
            return ReferrerNotFound(fromCache = false)
        }

        val condensedSuffix = suffix.take(2)
        Timber.i("Found suffix $condensedSuffix (looking for $prefix, found in $part)")
        return CampaignReferrerFound(condensedSuffix)
    }

    private fun stripCampaignName(
        fullCampaignName: String,
        prefix: String
    ): String {
        return fullCampaignName.substringAfter(prefix, "")
    }

    private fun splitIntoConstituentParts(referrer: String?): List<String>? {
        return referrer?.split("&")
    }

    companion object {
        private const val CAMPAIGN_NAME_PREFIX = "DDGRA"

        private const val INSTALLATION_SOURCE_KEY = "utm_source"
        private const val INSTALLATION_SOURCE_EU_AUCTION_VALUE = "eea-search-choice"
    }
}

sealed class ParsedReferrerResult(open val fromCache: Boolean = false) {
    data class EuAuctionReferrerFound(override val fromCache: Boolean = false) : ParsedReferrerResult(fromCache)
    data class CampaignReferrerFound(
        val campaignSuffix: String,
        override val fromCache: Boolean = false
    ) : ParsedReferrerResult(fromCache)

    data class ReferrerNotFound(override val fromCache: Boolean = false) : ParsedReferrerResult(fromCache)
    data class ParseFailure(val reason: ParseFailureReason) : ParsedReferrerResult()
    object ReferrerInitialising : ParsedReferrerResult()
}

sealed class ParseFailureReason {
    object FeatureNotSupported : ParseFailureReason()
    object ServiceUnavailable : ParseFailureReason()
    object DeveloperError : ParseFailureReason()
    object ServiceDisconnected : ParseFailureReason()
    object UnknownError : ParseFailureReason()
    object ReferralServiceUnavailable : ParseFailureReason()
}
