/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.promo.messaging

import com.squareup.moshi.Json

data class PromoPayloadJS(
    val record: PromoRecordJS?,
    val isNewInstall: Boolean,
    val aiChatEnabled: Boolean,
)

data class PromoRecordJS(
    val dismissedCampaignName: String?,
    val campaignName: String?,
    val bonusId: String?,
    val terms: PromoTermsJS?,
    val lastRedeemAttemptAt: Long?,
)

data class PromoTermsJS(
    val multiplier: Float,
    val expiresAt: Long,
)

data class GetNewPromoRecordParamsJS(
    val campaignName: String,
)

sealed interface PromoRedeemOutcomeParamsJS {
    val bonusId: String

    data class Success(
        override val bonusId: String,
        val grant: PromoTermsJS,
    ) : PromoRedeemOutcomeParamsJS

    data class Failure(
        override val bonusId: String,
        val definitive: Boolean,
    ) : PromoRedeemOutcomeParamsJS
}

data class PromoDismissParamsJS(
    val campaignName: String,
)

data class PromoEndParamsJS(
    val reason: PromoEndReasonJS,
)

enum class PromoEndReasonJS {
    @Json(name = "optedOut")
    OPTED_OUT,

    @Json(name = "expired")
    EXPIRED,

    @Json(name = "subscribed")
    SUBSCRIBED,
}
