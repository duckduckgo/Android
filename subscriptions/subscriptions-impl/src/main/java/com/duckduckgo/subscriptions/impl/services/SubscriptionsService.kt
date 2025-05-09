/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.services

import com.duckduckgo.anvil.annotations.ContributesNonCachingServiceApi
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.auth.AuthRequired
import com.duckduckgo.subscriptions.impl.model.Entitlement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@ContributesNonCachingServiceApi(AppScope::class)
interface SubscriptionsService {
    @AuthRequired
    @GET("https://subscriptions.duckduckgo.com/api/subscription")
    suspend fun subscription(): SubscriptionResponse

    @AuthRequired
    @GET("https://subscriptions.duckduckgo.com/api/checkout/portal")
    suspend fun portal(): PortalResponse

    @AuthRequired
    @GET("https://subscriptions.duckduckgo.com/api/v1/offer-status")
    suspend fun offerStatus(): OfferStatusResponse

    @AuthRequired
    @POST("https://subscriptions.duckduckgo.com/api/purchase/confirm/google")
    suspend fun confirm(
        @Body confirmationBody: ConfirmationBody,
    ): ConfirmationResponse

    @AuthRequired
    @POST("https://subscriptions.duckduckgo.com/api/feedback")
    suspend fun feedback(
        @Body feedbackBody: FeedbackBody,
    ): FeedbackResponse

    @GET("https://subscriptions.duckduckgo.com/api/products/{sku}/features")
    suspend fun features(@Path("sku") sku: String): FeaturesResponse
}

data class PortalResponse(val customerPortalUrl: String)

data class SubscriptionResponse(
    val productId: String,
    val billingPeriod: String,
    val startedAt: Long,
    val expiresOrRenewsAt: Long,
    val platform: String,
    val status: String,
    val activeOffers: List<ActiveOfferResponse>,
)

data class ActiveOfferResponse(
    val type: String,
)

data class ConfirmationBody(
    val packageName: String,
    val purchaseToken: String,
    val experimentName: String?,
    val experimentCohort: String?,
)

data class ConfirmationResponse(
    val email: String,
    val entitlements: List<ConfirmationEntitlement>,
    val subscription: SubscriptionResponse,
)

data class ConfirmationEntitlement(
    val product: String,
    val name: String,
)

fun List<ConfirmationEntitlement>.toEntitlements(): List<Entitlement> {
    return this.map { Entitlement(it.name, it.product) }
}

data class FeedbackBody(
    val userEmail: String,
    val platform: String = "android",
    val feedbackSource: String,
    val problemCategory: String,
    val customMetadata: String?,
    val feedbackText: String?,
    val appName: String?,
    val appPackage: String?,
    val problemSubCategory: String?,
)

data class FeedbackResponse(
    val message: String,
)

data class FeaturesResponse(
    val features: List<String>,
)

data class OfferStatusResponse(
    val hadTrial: Boolean,
)
