/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique

enum class SubscriptionPixel(
    private val baseName: String,
    private val types: Set<PixelType>,
    private val withSuffix: Boolean = true,
) {
    SUBSCRIPTION_ACTIVE(
        baseName = "m_privacy-pro_app_subscription_active",
        type = Daily(),
    ),
    OFFER_SCREEN_SHOWN(
        baseName = "m_privacy-pro_offer_screen_impression",
        type = Count,
    ),
    OFFER_SUBSCRIBE_CLICK(
        baseName = "m_privacy-pro_terms-conditions_subscribe_click",
        types = setOf(Count, Daily()),
    ),
    PURCHASE_FAILURE_OTHER(
        baseName = "m_privacy-pro_app_subscription-purchase_failure_other",
        types = setOf(Count, Daily()),
    ),
    PURCHASE_FAILURE_STORE(
        baseName = "m_privacy-pro_app_subscription-purchase_failure_store",
        types = setOf(Count, Daily()),
    ),
    PURCHASE_FAILURE_BACKEND(
        baseName = "m_privacy-pro_app_subscription-purchase_failure_backend",
        types = setOf(Count, Daily()),
    ),
    PURCHASE_FAILURE_ACCOUNT_CREATION(
        baseName = "m_privacy-pro_app_subscription-purchase_failure_account-creation",
        types = setOf(Count, Daily()),
    ),
    PURCHASE_SUCCESS(
        baseName = "m_privacy-pro_app_subscription-purchase_success",
        types = setOf(Count, Daily()),
    ),
    PURCHASE_SUCCESS_ORIGIN(
        baseName = "m_subscribe",
        type = Count,
        withSuffix = false,
    ),
    OFFER_RESTORE_PURCHASE_CLICK(
        baseName = "m_privacy-pro_offer_restore-purchase_click",
        type = Count,
    ),
    ACTIVATE_SUBSCRIPTION_ENTER_EMAIL_CLICK(
        baseName = "m_privacy-pro_activate-subscription_enter-email_click",
        types = setOf(Count, Daily()),
    ),
    ACTIVATE_SUBSCRIPTION_RESTORE_PURCHASE_CLICK(
        baseName = "m_privacy-pro_activate-subscription_restore-purchase_click",
        types = setOf(Count, Daily()),
    ),
    RESTORE_USING_EMAIL_SUCCESS(
        baseName = "m_privacy-pro_app_subscription-restore-using-email_success",
        types = setOf(Count, Daily()),
    ),
    RESTORE_USING_STORE_SUCCESS(
        baseName = "m_privacy-pro_app_subscription-restore-using-store_success",
        types = setOf(Count, Daily()),
    ),
    RESTORE_USING_STORE_FAILURE_SUBSCRIPTION_NOT_FOUND(
        baseName = "m_privacy-pro_app_subscription-restore-using-store_failure_not-found",
        types = setOf(Count, Daily()),
    ),
    RESTORE_USING_STORE_FAILURE_OTHER(
        baseName = "m_privacy-pro_app_subscription-restore-using-store_failure_other",
        types = setOf(Count, Daily()),
    ),
    RESTORE_AFTER_PURCHASE_ATTEMPT_SUCCESS(
        baseName = "m_privacy-pro_app_subscription-restore-after-purchase-attempt_success",
        type = Count,
    ),
    SUBSCRIPTION_ACTIVATED(
        baseName = "m_privacy-pro_app_subscription_activated",
        type = Unique(),
    ),
    ONBOARDING_ADD_DEVICE_CLICK(
        baseName = "m_privacy-pro_welcome_add-device_click",
        type = Unique(),
    ),
    ADD_DEVICE_ENTER_EMAIL_CLICK(
        baseName = "m_privacy-pro_add-device_enter-email_click",
        type = Count,
    ),
    ONBOARDING_VPN_CLICK(
        baseName = "m_privacy-pro_welcome_vpn_click",
        type = Unique(),
    ),
    ONBOARDING_PIR_CLICK(
        baseName = "m_privacy-pro_welcome_personal-information-removal_click",
        type = Unique(),
    ),
    ONBOARDING_IDTR_CLICK(
        baseName = "m_privacy-pro_welcome_identity-theft-restoration_click",
        type = Unique(),
    ),
    SUBSCRIPTION_SETTINGS_SHOWN(
        baseName = "m_privacy-pro_settings_screen_impression",
        type = Count,
    ),
    APP_SETTINGS_PIR_CLICK(
        baseName = "m_privacy-pro_app-settings_personal-information-removal_click",
        type = Count,
    ),
    APP_SETTINGS_IDTR_CLICK(
        baseName = "m_privacy-pro_app-settings_identity-theft-restoration_click",
        type = Count,
    ),
    APP_SETTINGS_RESTORE_PURCHASE_CLICK(
        baseName = "m_privacy-pro_app-settings_restore-purchase_click",
        type = Count,
    ),
    SUBSCRIPTION_SETTINGS_CHANGE_PLAN_OR_BILLING_CLICK(
        baseName = "m_privacy-pro_settings_change-plan-or-billing_click",
        type = Count,
    ),
    SUBSCRIPTION_SETTINGS_REMOVE_FROM_DEVICE_CLICK(
        baseName = "m_privacy-pro_settings_remove-from-device_click",
        type = Count,
    ),
    SUBSCRIPTION_PRICE_MONTHLY_CLICK(
        baseName = "m_privacy-pro_offer_monthly-price_click",
        type = Count,
    ),
    SUBSCRIPTION_PRICE_YEARLY_CLICK(
        baseName = "m_privacy-pro_offer_yearly-price_click",
        type = Count,
    ),
    SUBSCRIPTION_ONBOARDING_FAQ_CLICK(
        baseName = "m_privacy-pro_welcome_faq_click",
        type = Unique(),
    ),
    SUBSCRIPTION_ADD_EMAIL_SUCCESS(
        baseName = "m_privacy-pro_app_add-email_success",
        type = Unique(),
    ),
    SUBSCRIPTION_PRIVACY_PRO_REDIRECT(
        baseName = "m_privacy-pro_app_redirect",
        type = Count,
    ),
    ;

    constructor(
        baseName: String,
        type: PixelType,
        withSuffix: Boolean = true,
    ) : this(baseName, setOf(type), withSuffix)

    fun getPixelNames(): Map<PixelType, String> =
        types.associateWith { type -> if (withSuffix) "${baseName}_${type.pixelNameSuffix}" else baseName }
}

internal val PixelType.pixelNameSuffix: String
    get() = when (this) {
        is Count -> "c"
        is Daily -> "d"
        is Unique -> "u"
    }
