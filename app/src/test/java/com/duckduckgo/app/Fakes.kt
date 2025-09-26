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

package com.duckduckgo.app

import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting
import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting.ASK_EVERY_TIME
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browser.api.autocomplete.AutoCompleteSettings
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition

class FakeSettingsDataStore :
    SettingsDataStore,
    AutoCompleteSettings {
    private val store = mutableMapOf<String, Any?>()

    override var lastExecutedJobId: String?
        get() = store["lastExecutedJobId"] as String?
        set(value) {
            store["lastExecutedJobId"] = value
        }

    @Deprecated("hideTips variable is deprecated and no longer available in onboarding")
    override var hideTips: Boolean
        get() = store["hideTips"] as Boolean? ?: false
        set(value) {
            store["hideTips"] = value
        }

    override var autoCompleteSuggestionsEnabled: Boolean
        get() = store["autoCompleteSuggestionsEnabled"] as Boolean? ?: true
        set(value) {
            store["autoCompleteSuggestionsEnabled"] = value
        }

    override var maliciousSiteProtectionEnabled: Boolean
        get() = store["maliciousSiteProtectionEnabled"] as Boolean? ?: true
        set(value) {
            store["maliciousSiteProtectionEnabled"] = value
        }

    @Deprecated("Not used anymore after adding automatic fireproof", replaceWith = ReplaceWith("automaticFireproofSetting"))
    override var appLoginDetection: Boolean
        get() = store["appLoginDetection"] as Boolean? ?: true
        set(value) {
            store["appLoginDetection"] = value
        }

    override var automaticFireproofSetting: AutomaticFireproofSetting
        get() = store["automaticFireproofSetting"] as AutomaticFireproofSetting? ?: ASK_EVERY_TIME
        set(value) {
            store["automaticFireproofSetting"] = value
        }

    override var appLocationPermission: Boolean
        get() = store["appLocationPermission"] as Boolean? ?: true
        set(value) {
            store["appLocationPermission"] = value
        }

    override var appLocationPermissionDeniedForever: Boolean
        get() = store["appLocationPermissionDeniedForever"] as Boolean? ?: false
        set(value) {
            store["appLocationPermissionDeniedForever"] = value
        }

    override var appLocationPermissionMigrated: Boolean
        get() = store["appLocationPermissionMigrated"] as Boolean? ?: false
        set(value) {
            store["appLocationPermissionMigrated"] = value
        }

    override var appIcon: AppIcon
        get() = store["appIcon"] as AppIcon? ?: defaultIcon()
        set(value) {
            store["appIcon"] = value
        }

    override var selectedFireAnimation: FireAnimation
        get() = store["selectedFireAnimation"] as FireAnimation? ?: FireAnimation.HeroFire
        set(value) {
            store["selectedFireAnimation"] = value
        }

    override val fireAnimationEnabled: Boolean
        get() = selectedFireAnimation.resId != -1

    override var appIconChanged: Boolean
        get() = store["appIconChanged"] as Boolean? ?: false
        set(value) {
            store["appIconChanged"] = value
        }

    override var appUsedSinceLastClear: Boolean
        get() = store["appUsedSinceLastClear"] as Boolean? ?: true
        set(value) {
            store["appUsedSinceLastClear"] = value
        }

    override var automaticallyClearWhatOption: ClearWhatOption
        get() = store["automaticallyClearWhatOption"] as ClearWhatOption? ?: ClearWhatOption.CLEAR_NONE
        set(value) {
            store["automaticallyClearWhatOption"] = value
        }

    override var automaticallyClearWhenOption: ClearWhenOption
        get() = store["automaticallyClearWhenOption"] as ClearWhenOption? ?: ClearWhenOption.APP_EXIT_ONLY
        set(value) {
            store["automaticallyClearWhenOption"] = value
        }

    override var appBackgroundedTimestamp: Long
        get() = store["appBackgroundedTimestamp"] as Long? ?: 0L
        set(value) {
            store["appBackgroundedTimestamp"] = value
        }

    override var appNotificationsEnabled: Boolean
        get() = store["appNotificationsEnabled"] as Boolean? ?: true
        set(value) {
            store["appNotificationsEnabled"] = value
        }

    override var globalPrivacyControlEnabled: Boolean
        get() = store["globalPrivacyControlEnabled"] as Boolean? ?: true
        set(value) {
            store["globalPrivacyControlEnabled"] = value
        }

    override var appLinksEnabled: Boolean
        get() = store["appLinksEnabled"] as Boolean? ?: true
        set(value) {
            store["appLinksEnabled"] = value
        }

    override var showAppLinksPrompt: Boolean
        get() = store["showAppLinksPrompt"] as Boolean? ?: true
        set(value) {
            store["showAppLinksPrompt"] = value
        }

    override var showAutomaticFireproofDialog: Boolean
        get() = store["showAutomaticFireproofDialog"] as Boolean? ?: true
        set(value) {
            store["showAutomaticFireproofDialog"] = value
        }

    override var omnibarPosition: OmnibarPosition
        get() = OmnibarPosition.valueOf(store["omnibarPosition"] as String)
        set(value) {
            store["omnibarPosition"] = value.name
        }

    override var notifyMeInDownloadsDismissed: Boolean
        get() = store["notifyMeInDownloadsDismissed"] as Boolean? ?: false
        set(value) {
            store["notifyMeInDownloadsDismissed"] = value
        }

    override var experimentalWebsiteDarkMode: Boolean
        get() = store["experimentalWebsiteDarkMode"] as Boolean? ?: false
        set(value) {
            store["experimentalWebsiteDarkMode"] = value
        }

    override var isFullUrlEnabled: Boolean
        get() = store["isFullUrlEnabled"] as Boolean? ?: false
        set(value) {
            store["isFullUrlEnabled"] = value
        }

    override var clearDuckAiData: Boolean
        get() = store["clearDuckAiData"] as Boolean? ?: false
        set(value) {
            store["clearDuckAiData"] = value
        }

    override fun isCurrentlySelected(clearWhatOption: ClearWhatOption): Boolean {
        val currentlySelected = store["automaticallyClearWhatOption"] as ClearWhatOption?
        return currentlySelected == clearWhatOption
    }

    override fun isCurrentlySelected(clearWhenOption: ClearWhenOption): Boolean {
        val currentlySelected = store["automaticallyClearWhenOption"] as ClearWhenOption?
        return currentlySelected == clearWhenOption
    }

    override fun isCurrentlySelected(fireAnimation: FireAnimation): Boolean = selectedFireAnimation == fireAnimation

    override fun hasBackgroundTimestampRecorded(): Boolean = store.containsKey("appBackgroundedTimestamp")

    override fun clearAppBackgroundTimestamp() {
        store.remove("appBackgroundedTimestamp")
    }

    private fun defaultIcon(): AppIcon = AppIcon.DEFAULT
}
