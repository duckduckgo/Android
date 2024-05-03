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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.extensions.toSanitizedLanguageTag
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.ACTIVATE_SUBSCRIPTION_ENTER_EMAIL_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.ACTIVATE_SUBSCRIPTION_RESTORE_PURCHASE_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.ADD_DEVICE_ENTER_EMAIL_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.APP_SETTINGS_IDTR_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.APP_SETTINGS_PIR_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.APP_SETTINGS_RESTORE_PURCHASE_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.OFFER_RESTORE_PURCHASE_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.OFFER_SCREEN_SHOWN
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.OFFER_SUBSCRIBE_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.ONBOARDING_ADD_DEVICE_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.ONBOARDING_IDTR_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.ONBOARDING_PIR_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.ONBOARDING_VPN_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PURCHASE_FAILURE_ACCOUNT_CREATION
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PURCHASE_FAILURE_BACKEND
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PURCHASE_FAILURE_OTHER
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PURCHASE_FAILURE_STORE
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PURCHASE_SUCCESS
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.PURCHASE_SUCCESS_ORIGIN
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.RESTORE_AFTER_PURCHASE_ATTEMPT_SUCCESS
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.RESTORE_USING_EMAIL_SUCCESS
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.RESTORE_USING_STORE_FAILURE_OTHER
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.RESTORE_USING_STORE_FAILURE_SUBSCRIPTION_NOT_FOUND
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.RESTORE_USING_STORE_SUCCESS
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.SETTINGS_ADD_DEVICE_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.SUBSCRIPTION_ACTIVATED
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.SUBSCRIPTION_ACTIVE
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.SUBSCRIPTION_ADD_EMAIL_SUCCESS
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.SUBSCRIPTION_ONBOARDING_FAQ_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.SUBSCRIPTION_PRICE_MONTHLY_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.SUBSCRIPTION_PRICE_YEARLY_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.SUBSCRIPTION_PRIVACY_PRO_REDIRECT
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.SUBSCRIPTION_SETTINGS_CHANGE_PLAN_OR_BILLING_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.SUBSCRIPTION_SETTINGS_REMOVE_FROM_DEVICE_CLICK
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixel.SUBSCRIPTION_SETTINGS_SHOWN
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SubscriptionPixelSender {
    fun reportSubscriptionActive()
    fun reportOfferScreenShown()
    fun reportOfferSubscribeClick()
    fun reportPurchaseFailureOther()
    fun reportPurchaseFailureStore(errorType: String)
    fun reportPurchaseFailureBackend()
    fun reportPurchaseFailureAccountCreation()
    fun reportPurchaseSuccess()
    fun reportPurchaseSuccessOrigin(origin: String?)
    fun reportOfferRestorePurchaseClick()
    fun reportActivateSubscriptionEnterEmailClick()
    fun reportActivateSubscriptionRestorePurchaseClick()
    fun reportRestoreUsingEmailSuccess()
    fun reportRestoreUsingStoreSuccess()
    fun reportRestoreUsingStoreFailureSubscriptionNotFound()
    fun reportRestoreUsingStoreFailureOther()
    fun reportRestoreAfterPurchaseAttemptSuccess()
    fun reportSubscriptionActivated()
    fun reportOnboardingAddDeviceClick()
    fun reportSettingsAddDeviceClick()
    fun reportAddDeviceEnterEmailClick()
    fun reportOnboardingVpnClick()
    fun reportOnboardingPirClick()
    fun reportOnboardingIdtrClick()
    fun reportSubscriptionSettingsShown()
    fun reportAppSettingsPirClick()
    fun reportAppSettingsIdtrClick()
    fun reportAppSettingsRestorePurchaseClick()
    fun reportSubscriptionSettingsChangePlanOrBillingClick()
    fun reportSubscriptionSettingsRemoveFromDeviceClick()
    fun reportMonthlyPriceClick()
    fun reportYearlyPriceClick()
    fun reportOnboardingFaqClick()
    fun reportAddEmailSuccess()
    fun reportPrivacyProRedirect()
}

@ContributesBinding(AppScope::class)
class SubscriptionPixelSenderImpl @Inject constructor(
    private val pixelSender: Pixel,
    private val appBuildConfig: AppBuildConfig,
) : SubscriptionPixelSender {

    override fun reportSubscriptionActive() =
        fire(SUBSCRIPTION_ACTIVE)

    override fun reportOfferScreenShown() =
        fire(OFFER_SCREEN_SHOWN)

    override fun reportOfferSubscribeClick() =
        fire(OFFER_SUBSCRIBE_CLICK)

    override fun reportPurchaseFailureOther() =
        fire(PURCHASE_FAILURE_OTHER)

    override fun reportPurchaseFailureStore(errorType: String) =
        fire(PURCHASE_FAILURE_STORE, mapOf("errorType" to errorType))

    override fun reportPurchaseFailureBackend() =
        fire(PURCHASE_FAILURE_BACKEND)

    override fun reportPurchaseFailureAccountCreation() =
        fire(PURCHASE_FAILURE_ACCOUNT_CREATION)

    override fun reportPurchaseSuccess() =
        fire(PURCHASE_SUCCESS)

    override fun reportPurchaseSuccessOrigin(origin: String?) {
        val map = mutableMapOf(
            "locale" to appBuildConfig.deviceLocale.toSanitizedLanguageTag(),
        )
        origin?.let {
            map.put("origin", origin)
        }
        fire(PURCHASE_SUCCESS_ORIGIN, map)
    }

    override fun reportOfferRestorePurchaseClick() =
        fire(OFFER_RESTORE_PURCHASE_CLICK)

    override fun reportActivateSubscriptionEnterEmailClick() =
        fire(ACTIVATE_SUBSCRIPTION_ENTER_EMAIL_CLICK)

    override fun reportActivateSubscriptionRestorePurchaseClick() =
        fire(ACTIVATE_SUBSCRIPTION_RESTORE_PURCHASE_CLICK)

    override fun reportRestoreUsingEmailSuccess() =
        fire(RESTORE_USING_EMAIL_SUCCESS)

    override fun reportRestoreUsingStoreSuccess() =
        fire(RESTORE_USING_STORE_SUCCESS)

    override fun reportRestoreUsingStoreFailureSubscriptionNotFound() =
        fire(RESTORE_USING_STORE_FAILURE_SUBSCRIPTION_NOT_FOUND)

    override fun reportRestoreUsingStoreFailureOther() =
        fire(RESTORE_USING_STORE_FAILURE_OTHER)

    override fun reportRestoreAfterPurchaseAttemptSuccess() =
        fire(RESTORE_AFTER_PURCHASE_ATTEMPT_SUCCESS)

    override fun reportSubscriptionActivated() =
        fire(SUBSCRIPTION_ACTIVATED)

    override fun reportOnboardingAddDeviceClick() =
        fire(ONBOARDING_ADD_DEVICE_CLICK)

    override fun reportSettingsAddDeviceClick() =
        fire(SETTINGS_ADD_DEVICE_CLICK)

    override fun reportAddDeviceEnterEmailClick() =
        fire(ADD_DEVICE_ENTER_EMAIL_CLICK)

    override fun reportOnboardingVpnClick() =
        fire(ONBOARDING_VPN_CLICK)

    override fun reportOnboardingPirClick() =
        fire(ONBOARDING_PIR_CLICK)

    override fun reportOnboardingIdtrClick() =
        fire(ONBOARDING_IDTR_CLICK)

    override fun reportSubscriptionSettingsShown() =
        fire(SUBSCRIPTION_SETTINGS_SHOWN)

    override fun reportAppSettingsPirClick() =
        fire(APP_SETTINGS_PIR_CLICK)

    override fun reportAppSettingsIdtrClick() =
        fire(APP_SETTINGS_IDTR_CLICK)

    override fun reportAppSettingsRestorePurchaseClick() =
        fire(APP_SETTINGS_RESTORE_PURCHASE_CLICK)

    override fun reportSubscriptionSettingsChangePlanOrBillingClick() =
        fire(SUBSCRIPTION_SETTINGS_CHANGE_PLAN_OR_BILLING_CLICK)

    override fun reportSubscriptionSettingsRemoveFromDeviceClick() =
        fire(SUBSCRIPTION_SETTINGS_REMOVE_FROM_DEVICE_CLICK)

    override fun reportMonthlyPriceClick() =
        fire(SUBSCRIPTION_PRICE_MONTHLY_CLICK)

    override fun reportYearlyPriceClick() =
        fire(SUBSCRIPTION_PRICE_YEARLY_CLICK)

    override fun reportOnboardingFaqClick() =
        fire(SUBSCRIPTION_ONBOARDING_FAQ_CLICK)

    override fun reportAddEmailSuccess() =
        fire(SUBSCRIPTION_ADD_EMAIL_SUCCESS)

    override fun reportPrivacyProRedirect() =
        fire(SUBSCRIPTION_PRIVACY_PRO_REDIRECT)

    private fun fire(pixel: SubscriptionPixel, params: Map<String, String> = emptyMap()) {
        pixel.getPixelNames().forEach { (pixelType, pixelName) ->
            pixelSender.fire(pixelName = pixelName, type = pixelType, parameters = params)
        }
    }
}
