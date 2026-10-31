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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.DuckChatConstants.JS_MESSAGING_FEATURE_NAME
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.promo.PromoNewInstallChecker
import com.duckduckgo.duckchat.impl.promo.data.PromoCampaignRepository
import com.duckduckgo.duckchat.impl.promo.data.PromoRecord
import com.duckduckgo.duckchat.impl.promo.data.PromoRecordCorruptedException
import com.duckduckgo.duckchat.impl.promo.data.PromoRecordStorageException
import com.duckduckgo.duckchat.impl.promo.messaging.PromoCampaignJSHelper.Companion.METHOD_BONUS_DISMISS
import com.duckduckgo.duckchat.impl.promo.messaging.PromoCampaignJSHelper.Companion.METHOD_BONUS_END
import com.duckduckgo.duckchat.impl.promo.messaging.PromoCampaignJSHelper.Companion.METHOD_BONUS_REDEEM_OUTCOME
import com.duckduckgo.duckchat.impl.promo.messaging.PromoCampaignJSHelper.Companion.METHOD_GET_BONUS_RECORD
import com.duckduckgo.duckchat.impl.promo.messaging.PromoCampaignJSHelper.Companion.METHOD_GET_NEW_BONUS_RECORD
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import logcat.LogPriority.WARN
import logcat.logcat
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

/**
 * Exposes the promo record to Duck.ai.
 * Messages are ignored and nothing is emitted while promo campaigns are disabled.
 */
interface PromoCampaignJSHelper {
    val promoRecordEvents: Flow<SubscriptionEventData>

    /**
     * @return the response to [method], or null if it has no response or the stored record can't be read or written.
     */
    suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        requestId: String?,
        data: JSONObject?,
    ): JsCallbackData?

    companion object {
        const val METHOD_GET_BONUS_RECORD = "getAIChatBonusRecord"
        const val METHOD_GET_NEW_BONUS_RECORD = "getAIChatNewBonusRecord"
        const val METHOD_BONUS_REDEEM_OUTCOME = "aiChatBonusRedeemOutcome"
        const val METHOD_BONUS_DISMISS = "aiChatBonusDismiss"
        const val METHOD_BONUS_END = "aiChatBonusEnd"
    }
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealPromoCampaignJSHelper @Inject constructor(
    private val promoCampaignRepository: PromoCampaignRepository,
    private val promoNewInstallChecker: PromoNewInstallChecker,
    private val duckChat: DuckChat,
    private val duckChatFeature: DuckChatFeature,
    moshi: Moshi,
    dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : PromoCampaignJSHelper {
    private val moshi: Moshi by lazy {
        moshi.newBuilder()
            .add(
                PolymorphicJsonAdapterFactory.of(PromoRedeemOutcomeParamsJS::class.java, REDEEM_RESULT)
                    .withSubtype(PromoRedeemOutcomeParamsJS.Success::class.java, REDEEM_RESULT_SUCCESS)
                    .withSubtype(PromoRedeemOutcomeParamsJS.Failure::class.java, REDEEM_RESULT_FAILURE),
            )
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    override val promoRecordEvents: Flow<SubscriptionEventData> = promoCampaignRepository.promoRecord
        .filter { duckChatFeature.duckAiPromoCampaigns().isEnabled() }
        .map { record ->
            SubscriptionEventData(
                featureName = JS_MESSAGING_FEATURE_NAME,
                subscriptionName = SUBSCRIPTION_BONUS_RECORD,
                params = createPayload(record),
            )
        }
        .catch { cause ->
            if (cause !is PromoRecordCorruptedException) throw cause
            logcat(WARN) { "Duck.ai promo: stopped pushing the promo record, it can't be decoded" }
        }
        .flowOn(dispatcherProvider.io())
        .shareIn(appCoroutineScope, SharingStarted.WhileSubscribed(), replay = 0)

    override suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        requestId: String?,
        data: JSONObject?,
    ): JsCallbackData? {
        if (!duckChatFeature.duckAiPromoCampaigns().isEnabled()) {
            return null
        }
        val params = data ?: JSONObject()
        return try {
            when (method) {
                METHOD_GET_BONUS_RECORD -> {
                    if (requestId == null) {
                        return null
                    }
                    val record = promoCampaignRepository.getPromoRecord()
                    JsCallbackData(createPayload(record), featureName, method, requestId)
                }

                METHOD_GET_NEW_BONUS_RECORD -> {
                    if (requestId == null) {
                        return null
                    }
                    val campaignId = params
                        .toParams<GetNewPromoRecordParamsJS>()
                        .campaignName
                        .takeIf { it.isNotEmpty() }
                        ?: return null
                    val record = promoCampaignRepository.initializePromo(campaignId, promoId = UUID.randomUUID())
                    JsCallbackData(createPayload(record), featureName, method, requestId)
                }

                METHOD_BONUS_REDEEM_OUTCOME -> {
                    val outcome = params.toParams<PromoRedeemOutcomeParamsJS>()
                    val promoId = try {
                        UUID.fromString(outcome.bonusId)
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                    if (promoId == null) {
                        return null
                    }
                    when (outcome) {
                        is PromoRedeemOutcomeParamsJS.Success -> {
                            promoCampaignRepository.startPromo(promoId, outcome.grant.toPromoTerms())
                        }

                        is PromoRedeemOutcomeParamsJS.Failure -> {
                            if (outcome.definitive) {
                                promoCampaignRepository.invalidatePromo(promoId)
                            } else {
                                promoCampaignRepository.recordRedeemAttempt(promoId)
                            }
                        }
                    }
                    null
                }

                METHOD_BONUS_DISMISS -> {
                    val campaignId = params
                        .toParams<PromoDismissParamsJS>()
                        .campaignName
                        .takeIf { it.isNotEmpty() }
                        ?: return null
                    promoCampaignRepository.dismissCampaign(campaignId)
                    null
                }

                METHOD_BONUS_END -> {
                    promoCampaignRepository.endPromo(checkExpiry = false)
                    null
                }

                else -> null
            }
        } catch (_: JsonDataException) {
            logcat(WARN) { "Duck.ai promo: $method ignored, its params are invalid" }
            null
        } catch (_: PromoRecordStorageException) {
            logcat(WARN) { "Duck.ai promo: $method failed, the promo record can't be read or written" }
            null
        } catch (_: PromoRecordCorruptedException) {
            logcat(WARN) { "Duck.ai promo: $method failed, the promo record can't be decoded" }
            null
        }
    }

    private suspend fun createPayload(record: PromoRecord?): JSONObject {
        val payload = PromoPayloadJS(
            record = record?.toPromoRecordJS(),
            isNewInstall = promoNewInstallChecker.isNewInstall(NEW_INSTALL_WINDOW_DAYS),
            aiChatEnabled = duckChat.isEnabled(),
        )
        val jsonString = moshi.adapter(PromoPayloadJS::class.java).toJson(payload)
        return JSONObject(jsonString).apply {
            if (payload.record == null) {
                // Moshi leaves out null properties, but Duck.ai expects the record to be sent as null when there's none
                put(RECORD, JSONObject.NULL)
            }
        }
    }

    private inline fun <reified T> JSONObject.toParams(): T =
        moshi.adapter(T::class.java).fromJson(toString())
            ?: throw JsonDataException("Params are null")

    private companion object {
        const val SUBSCRIPTION_BONUS_RECORD = "submitAIChatBonusRecord"
        const val RECORD = "record"
        const val REDEEM_RESULT = "result"
        const val REDEEM_RESULT_SUCCESS = "success"
        const val REDEEM_RESULT_FAILURE = "failure"
        const val NEW_INSTALL_WINDOW_DAYS = 7
    }
}
