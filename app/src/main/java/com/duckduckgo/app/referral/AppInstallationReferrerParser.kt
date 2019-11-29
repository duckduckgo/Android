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
import timber.log.Timber


interface AppInstallationReferrerParser {

    fun parse(referrer: String): ParsedReferrerResult
}

class QueryParamReferrerParser(private val campaignPrefix: String = CAMPAIGN_NAME_PREFIX) : AppInstallationReferrerParser {

    override fun parse(referrer: String): ParsedReferrerResult {
        val referrerParts = splitIntoConstituentParts(referrer)
        if (referrerParts.isNullOrEmpty()) return ReferrerNotFound

        for (part in referrerParts) {
            Timber.d("Analysing query param part: $part")
            if (part.contains(campaignPrefix)) {
                Timber.i("Found target campaign name prefix $campaignPrefix in $part")
                val suffix = stripCampaignName(part)

                if (suffix.length < 2) {
                    Timber.w("Unexpected suffix length for campaign")
                    return ReferrerNotFound
                }

                Timber.i("Would send $suffix (looking for ${campaignPrefix}, found in $part)")
                return ReferrerFound(suffix.take(2))
            }
        }

        return ReferrerNotFound
    }

    private fun stripCampaignName(fullCampaignName: String): String {
        return fullCampaignName.substringAfter(campaignPrefix, "")
    }

    private fun splitIntoConstituentParts(referrer: String?): List<String>? {
        return referrer?.split("&")
    }

    companion object {
        private const val CAMPAIGN_NAME_PREFIX = "tCam"
    }
}

sealed class ParsedReferrerResult {
    data class ReferrerFound(val campaignSuffix: String) : ParsedReferrerResult()
    object ReferrerNotFound : ParsedReferrerResult()
    data class ParseFailure(val reason: ParseFailureReason) : ParsedReferrerResult()
}

sealed class ParseFailureReason {
    object FeatureNotSupported : ParseFailureReason()
    object ServiceUnavailable : ParseFailureReason()
    object DeveloperError : ParseFailureReason()
    object ServiceDisconnected : ParseFailureReason()
    object UnknownError : ParseFailureReason()
    object ReferralServiceUnavailable : ParseFailureReason()
}
