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
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.COUNT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.DAILY
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.UNIQUE
import java.util.EnumSet

enum class SubscriptionPixel(
    private val baseName: String,
    private val types: Set<PixelType>,
) {
    SETTINGS_SUBSCRIPTION_SECTION_SHOWN(
        baseName = "m_privacy-pro_app-settings_privacy-pro-section_impression",
        type = COUNT,
    ),
    SUBSCRIPTION_ACTIVE(
        baseName = "m_privacy-pro_app_subscription_active",
        type = DAILY,
    ),
    OFFER_SCREEN_SHOWN(
        baseName = "m_privacy-pro_offer_screen_impression",
        type = COUNT,
    ),
    OFFER_SUBSCRIBE_CLICK(
        baseName = "m_privacy-pro_terms-conditions_subscribe_click",
        types = EnumSet.of(COUNT, DAILY),
    ),
    PURCHASE_FAILURE_OTHER(
        baseName = "m_privacy-pro_app_subscription-purchase_failure_other",
        types = EnumSet.of(COUNT, DAILY),
    ),
    PURCHASE_FAILURE_STORE(
        baseName = "m_privacy-pro_app_subscription-purchase_failure_store",
        types = EnumSet.of(COUNT, DAILY),
    ),
    PURCHASE_FAILURE_BACKEND(
        baseName = "m_privacy-pro_app_subscription-purchase_failure_backend",
        types = EnumSet.of(COUNT, DAILY),
    ),
    PURCHASE_FAILURE_ACCOUNT_CREATION(
        baseName = "m_privacy-pro_app_subscription-purchase_failure_account-creation",
        types = EnumSet.of(COUNT, DAILY),
    ),
    PURCHASE_SUCCESS(
        baseName = "m_privacy-pro_app_subscription-purchase_success",
        types = EnumSet.of(COUNT, DAILY),
    ),
    OFFER_RESTORE_PURCHASE_CLICK(
        baseName = "m_privacy-pro_offer_restore-purchase_click",
        type = COUNT,
    ),
    ACTIVATE_SUBSCRIPTION_ENTER_EMAIL_CLICK(
        baseName = "m_privacy-pro_activate-subscription_enter-email_click",
        types = EnumSet.of(COUNT, DAILY),
    ),
    ACTIVATE_SUBSCRIPTION_RESTORE_PURCHASE_CLICK(
        baseName = "m_privacy-pro_activate-subscription_restore-purchase_click",
        types = EnumSet.of(COUNT, DAILY),
    ),
    RESTORE_USING_EMAIL_SUCCESS(
        baseName = "m_privacy-pro_app_subscription-restore-using-email_success",
        types = EnumSet.of(COUNT, DAILY),
    ),
    RESTORE_USING_STORE_SUCCESS(
        baseName = "m_privacy-pro_app_subscription-restore-using-store_success",
        types = EnumSet.of(COUNT, DAILY),
    ),
    RESTORE_USING_STORE_FAILURE_SUBSCRIPTION_NOT_FOUND(
        baseName = "m_privacy-pro_app_subscription-restore-using-store_failure_not-found",
        types = EnumSet.of(COUNT, DAILY),
    ),
    RESTORE_USING_STORE_FAILURE_OTHER(
        baseName = "m_privacy-pro_app_subscription-restore-using-store_failure_other",
        types = EnumSet.of(COUNT, DAILY),
    ),
    RESTORE_AFTER_PURCHASE_ATTEMPT_SUCCESS(
        baseName = "m_privacy-pro_app_subscription-restore-after-purchase-attempt_success",
        type = COUNT,
    ),
    SUBSCRIPTION_ACTIVATED(
        baseName = "m_privacy-pro_app_subscription_activated",
        type = UNIQUE,
    ),
    ONBOARDING_ADD_DEVICE_CLICK(
        baseName = "m_privacy-pro_welcome_add-device_click",
        type = UNIQUE,
    ),
    SETTINGS_ADD_DEVICE_CLICK(
        baseName = "m_privacy-pro_settings_add-device_click",
        type = COUNT,
    ),
    ADD_DEVICE_ENTER_EMAIL_CLICK(
        baseName = "m_privacy-pro_add-device_enter-email_click",
        type = COUNT,
    ),
    ONBOARDING_VPN_CLICK(
        baseName = "m_privacy-pro_welcome_vpn_click",
        type = UNIQUE,
    ),
    ONBOARDING_PIR_CLICK(
        baseName = "m_privacy-pro_welcome_personal-information-removal_click",
        type = UNIQUE,
    ),
    ONBOARDING_IDTR_CLICK(
        baseName = "m_privacy-pro_welcome_identity-theft-restoration_click",
        type = UNIQUE,
    ),
    SUBSCRIPTION_SETTINGS_SHOWN(
        baseName = "m_privacy-pro_settings_screen_impression",
        type = COUNT,
    ),
    APP_SETTINGS_PIR_CLICK(
        baseName = "m_privacy-pro_app-settings_personal-information-removal_click",
        type = COUNT,
    ),
    APP_SETTINGS_IDTR_CLICK(
        baseName = "m_privacy-pro_app-settings_identity-theft-restoration_click",
        type = COUNT,
    ),
    SUBSCRIPTION_SETTINGS_CHANGE_PLAN_OR_BILLING_CLICK(
        baseName = "m_privacy-pro_settings_change-plan-or-billing_click",
        type = COUNT,
    ),
    SUBSCRIPTION_SETTINGS_REMOVE_FROM_DEVICE_CLICK(
        baseName = "m_privacy-pro_settings_remove-from-device_click",
        type = COUNT,
    ),
    ;

    constructor(
        baseName: String,
        type: PixelType,
    ) : this(baseName, EnumSet.of(type))

    fun getPixelNames(): Map<PixelType, String> =
        types.associateWith { type -> "${baseName}_${type.pixelNameSuffix}" }
}

private val PixelType.pixelNameSuffix: String
    get() = when (this) {
        COUNT -> "c"
        DAILY -> "d"
        UNIQUE -> "u"
    }
