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

package com.duckduckgo.referral.api

/** Outcome of parsing the Play Store install referrer. */
sealed class ParsedReferrerResult {
    data object EuAuctionSearchChoiceReferrerFound : ParsedReferrerResult()
    data object EuAuctionBrowserChoiceReferrerFound : ParsedReferrerResult()

    /** A marketing campaign referrer; [campaignSuffix] is the extracted campaign code. */
    data class CampaignReferrerFound(val campaignSuffix: String) : ParsedReferrerResult()

    data object ReferrerNotFound : ParsedReferrerResult()
    data class ParseFailure(val reason: ParseFailureReason) : ParsedReferrerResult()

    /** Retrieval is still in flight; no result yet. */
    data object ReferrerInitialising : ParsedReferrerResult()
}

/** Reason referrer retrieval failed; mirrors Google Play Install Referrer responses. */
sealed class ParseFailureReason {
    data object FeatureNotSupported : ParseFailureReason()
    data object ServiceUnavailable : ParseFailureReason()
    data object DeveloperError : ParseFailureReason()
    data object ServiceDisconnected : ParseFailureReason()
    data object UnknownError : ParseFailureReason()
    data object ReferralServiceUnavailable : ParseFailureReason()
}
