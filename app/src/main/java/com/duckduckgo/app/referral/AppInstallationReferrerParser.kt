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

import com.duckduckgo.app.referral.ParsedReferrerResult.ReferrerFound
import com.duckduckgo.app.referral.ParsedReferrerResult.ReferrerNotFound
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.APP_REFERRER_FOUND_TYPE_A
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.APP_REFERRER_FOUND_TYPE_B
import timber.log.Timber


interface AppInstallationReferrerParser {

    fun parse(referrer: String): ParsedReferrerResult
}

class QueryParamReferrerParser(private val pixel: Pixel) : AppInstallationReferrerParser {

    override fun parse(referrer: String): ParsedReferrerResult {
        val referrerParts = splitIntoConstituentParts(referrer)
        if (referrerParts.isNullOrEmpty()) return ReferrerNotFound(fromCache = false)

        for (part in referrerParts) {
            Timber.d("Analysing query param part: $part")

            if (part.contains(CAMPAIGN_NAME_PREFIX_TYPE_A)) {
                val result = extractCampaignNameSuffix(part, CAMPAIGN_NAME_PREFIX_TYPE_A)
                sendPixelForResult(result, APP_REFERRER_FOUND_TYPE_A)
                return result
            }

            if (part.contains(CAMPAIGN_NAME_PREFIX_TYPE_B)) {
                val result = extractCampaignNameSuffix(part, CAMPAIGN_NAME_PREFIX_TYPE_B)
                sendPixelForResult(result, APP_REFERRER_FOUND_TYPE_B)
                return result
            }
        }

        Timber.i("Referrer information does not contain inspected campaign names")
        return ReferrerNotFound(fromCache = false)
    }

    private fun sendPixelForResult(result: ParsedReferrerResult, pixelName: Pixel.PixelName) {
        if (result is ReferrerFound) {
            val map = mapOf(PIXEL_KEY_CAMPAIGN_SUFFIX to result.campaignSuffix)
            pixel.fire(pixelName, map)
        }
    }

    private fun extractCampaignNameSuffix(part: String, prefix: String): ParsedReferrerResult {
        Timber.i("Found target campaign name prefix $prefix in $part")
        val suffix = stripCampaignName(part, prefix)

        if (suffix.length < 2) {
            Timber.w("Unexpected suffix length for campaign")
            return ReferrerNotFound(fromCache = false)
        }

        val condensedSuffix = suffix.take(2)
        Timber.i("Found suffix $condensedSuffix (looking for ${prefix}, found in $part)")
        return ReferrerFound(condensedSuffix)
    }

    private fun stripCampaignName(fullCampaignName: String, prefix: String): String {
        return fullCampaignName.substringAfter(prefix, "")
    }

    private fun splitIntoConstituentParts(referrer: String?): List<String>? {
        return referrer?.split("&")
    }

    companion object {
        private const val CAMPAIGN_NAME_PREFIX_TYPE_A = "DDGRA"
        private const val CAMPAIGN_NAME_PREFIX_TYPE_B = "DDGRB"

        private const val PIXEL_KEY_CAMPAIGN_SUFFIX = "c"
    }
}

sealed class ParsedReferrerResult(open val fromCache: Boolean = false) {
    data class ReferrerFound(val campaignSuffix: String, override val fromCache: Boolean = false) : ParsedReferrerResult(fromCache)
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
