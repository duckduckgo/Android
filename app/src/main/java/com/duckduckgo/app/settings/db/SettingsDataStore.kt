/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.settings.db

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting
import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting.ASK_EVERY_TIME
import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting.NEVER
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.autocomplete.AutoCompleteSettings
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface SettingsDataStore {

    var lastExecutedJobId: String?

    @Deprecated(message = "hideTips variable is deprecated and no longer available in onboarding")
    var hideTips: Boolean
    var maliciousSiteProtectionEnabled: Boolean
    var appIcon: AppIcon
    var selectedFireAnimation: FireAnimation
    val fireAnimationEnabled: Boolean
    var appIconChanged: Boolean

    @Deprecated(message = "Not used anymore after adding automatic fireproof", replaceWith = ReplaceWith(expression = "automaticFireproofSetting"))
    var appLoginDetection: Boolean
    var automaticFireproofSetting: AutomaticFireproofSetting

    @Deprecated(
        message = "Not used anymore after migration to SitePermissionsRepository - https://app.asana.com/0/1174433894299346/1206170291275949/f",
        replaceWith = ReplaceWith(expression = "SitePermissionsRepository.askLocationEnabled"),
    )
    var appLocationPermission: Boolean

    @Deprecated(
        message = "Not used anymore after migration to SitePermissionsRepository - https://app.asana.com/0/1174433894299346/1206170291275949/f",
        replaceWith = ReplaceWith(expression = "SitePermissionsRepository.askLocationEnabled"),
    )
    var appLocationPermissionDeniedForever: Boolean
    var appLocationPermissionMigrated: Boolean

    var globalPrivacyControlEnabled: Boolean
    var appLinksEnabled: Boolean
    var showAppLinksPrompt: Boolean
    var showAutomaticFireproofDialog: Boolean
    var omnibarPosition: OmnibarPosition

    /**
     * This will be checked upon app startup and used to decide whether it should perform a clear or not.
     * If the app is cleared and the process restarted as a result, then we don't want to clear and restart again when the app launches.
     * To avoid this extra clear, we can indicate whether the app could have been used since the last clear or not.
     * If the app is cleared in the background, or the process is being restarted, we can set this to false.
     * If the app is cleared in the foreground, but the process isn't restarted, we can set this to true.
     */
    var appUsedSinceLastClear: Boolean
    var automaticallyClearWhatOption: ClearWhatOption
    var automaticallyClearWhenOption: ClearWhenOption
    var appBackgroundedTimestamp: Long
    var appNotificationsEnabled: Boolean
    var notifyMeInDownloadsDismissed: Boolean
    var experimentalWebsiteDarkMode: Boolean
    var isFullUrlEnabled: Boolean

    fun isCurrentlySelected(clearWhatOption: ClearWhatOption): Boolean
    fun isCurrentlySelected(clearWhenOption: ClearWhenOption): Boolean
    fun isCurrentlySelected(fireAnimation: FireAnimation): Boolean
    fun hasBackgroundTimestampRecorded(): Boolean
    fun clearAppBackgroundTimestamp()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = SettingsDataStore::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = AutoCompleteSettings::class,
)
@SingleInstanceIn(AppScope::class)
class SettingsSharedPreferences @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
) : SettingsDataStore, AutoCompleteSettings {

    private val fireAnimationMapper = FireAnimationPrefsMapper()

    override var lastExecutedJobId: String?
        get() = preferences.getString(KEY_BACKGROUND_JOB_ID, null)
        set(value) {
            preferences.edit(commit = true) {
                if (value == null) {
                    remove(KEY_BACKGROUND_JOB_ID)
                } else {
                    putString(KEY_BACKGROUND_JOB_ID, value)
                }
            }
        }

    override var hideTips: Boolean
        get() = preferences.getBoolean(KEY_HIDE_TIPS, false)
        set(enabled) = preferences.edit { putBoolean(KEY_HIDE_TIPS, enabled) }

    override var autoCompleteSuggestionsEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTOCOMPLETE_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_AUTOCOMPLETE_ENABLED, enabled) }

    override var maliciousSiteProtectionEnabled: Boolean
        get() = preferences.getBoolean(KEY_MALICIOUS_SITE_PROTECTION_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_MALICIOUS_SITE_PROTECTION_ENABLED, enabled) }

    override var appLoginDetection: Boolean
        get() = preferences.getBoolean("KEY_LOGIN_DETECTION_ENABLED", true)
        set(enabled) = preferences.edit { putBoolean("KEY_LOGIN_DETECTION_ENABLED", enabled) }

    override var automaticFireproofSetting: AutomaticFireproofSetting
        get() = AutomaticFireproofSetting.valueOf(preferences.getString(KEY_AUTOMATIC_FIREPROOF_SETTING, ASK_EVERY_TIME.name) ?: ASK_EVERY_TIME.name)
        set(loginDetectionSetting) = preferences.edit { putString(KEY_AUTOMATIC_FIREPROOF_SETTING, loginDetectionSetting.name) }

    override var appLocationPermission: Boolean
        get() = preferences.getBoolean(KEY_SITE_LOCATION_PERMISSION_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_SITE_LOCATION_PERMISSION_ENABLED, enabled) }

    override var appLocationPermissionDeniedForever: Boolean
        get() = preferences.getBoolean(KEY_SYSTEM_LOCATION_PERMISSION_DENIED_FOREVER, false)
        set(enabled) = preferences.edit { putBoolean(KEY_SYSTEM_LOCATION_PERMISSION_DENIED_FOREVER, enabled) }

    override var appLocationPermissionMigrated: Boolean
        get() = preferences.getBoolean(KEY_SITE_LOCATION_PERMISSION_MIGRATED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_SITE_LOCATION_PERMISSION_MIGRATED, enabled) }

    override var appIcon: AppIcon
        get() {
            val componentName = preferences.getString(KEY_APP_ICON, defaultIcon().componentName) ?: return defaultIcon()
            return AppIcon.from(componentName)
        }
        set(appIcon) = preferences.edit(commit = true) { putString(KEY_APP_ICON, appIcon.componentName) }

    override var selectedFireAnimation: FireAnimation
        get() = selectedFireAnimationSavedValue()
        set(value) = preferences.edit { putString(KEY_SELECTED_FIRE_ANIMATION, fireAnimationMapper.prefValue(value)) }

    override val fireAnimationEnabled: Boolean
        get() = selectedFireAnimation.resId != -1

    // Changing the app icon makes the app close in some devices / OS versions. This is a problem if the user has
    // selected automatic data / tabs clear. We will use this flag to track if the user has changed the icon
    // and prevent the tabs / data from be cleared {check AutomaticDataClearer}
    override var appIconChanged: Boolean
        get() = preferences.getBoolean(KEY_APP_ICON_CHANGED, false)
        set(enabled) = preferences.edit(commit = true) { putBoolean(KEY_APP_ICON_CHANGED, enabled) }

    override var appUsedSinceLastClear: Boolean
        get() = preferences.getBoolean(KEY_APP_USED_SINCE_LAST_CLEAR, true)
        set(enabled) = preferences.edit(commit = true) { putBoolean(KEY_APP_USED_SINCE_LAST_CLEAR, enabled) }

    override var automaticallyClearWhatOption: ClearWhatOption
        get() = automaticallyClearWhatSavedValue() ?: ClearWhatOption.CLEAR_NONE
        set(value) = preferences.edit { putString(KEY_AUTOMATICALLY_CLEAR_WHAT_OPTION, value.name) }

    override var automaticallyClearWhenOption: ClearWhenOption
        get() = automaticallyClearWhenSavedValue() ?: ClearWhenOption.APP_EXIT_ONLY
        set(value) = preferences.edit { putString(KEY_AUTOMATICALLY_CLEAR_WHEN_OPTION, value.name) }

    override var appBackgroundedTimestamp: Long
        get() = preferences.getLong(KEY_APP_BACKGROUNDED_TIMESTAMP, 0)
        set(value) = preferences.edit(commit = true) { putLong(KEY_APP_BACKGROUNDED_TIMESTAMP, value) }

    override var appNotificationsEnabled: Boolean
        get() = preferences.getBoolean(KEY_APP_NOTIFICATIONS_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_APP_NOTIFICATIONS_ENABLED, enabled) }

    override var globalPrivacyControlEnabled: Boolean
        get() = preferences.getBoolean(KEY_DO_NOT_SELL_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_DO_NOT_SELL_ENABLED, enabled) }

    override var appLinksEnabled: Boolean
        get() = preferences.getBoolean(APP_LINKS_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(APP_LINKS_ENABLED, enabled) }

    override var showAppLinksPrompt: Boolean
        get() = preferences.getBoolean(SHOW_APP_LINKS_PROMPT, true)
        set(enabled) = preferences.edit { putBoolean(SHOW_APP_LINKS_PROMPT, enabled) }

    override var showAutomaticFireproofDialog: Boolean
        get() = preferences.getBoolean(SHOW_AUTOMATIC_FIREPROOF_DIALOG, true)
        set(enabled) = preferences.edit { putBoolean(SHOW_AUTOMATIC_FIREPROOF_DIALOG, enabled) }

    override var omnibarPosition: OmnibarPosition
        get() = OmnibarPosition.valueOf(preferences.getString(KEY_OMNIBAR_POSITION, OmnibarPosition.TOP.name) ?: OmnibarPosition.TOP.name)
        set(value) = preferences.edit { putString(KEY_OMNIBAR_POSITION, value.name) }

    override var isFullUrlEnabled: Boolean
        get() = preferences.getBoolean(KEY_IS_FULL_URL_ENABLED, true)
        set(enabled) = preferences.edit { putBoolean(KEY_IS_FULL_URL_ENABLED, enabled) }

    override fun hasBackgroundTimestampRecorded(): Boolean = preferences.contains(KEY_APP_BACKGROUNDED_TIMESTAMP)
    override fun clearAppBackgroundTimestamp() = preferences.edit { remove(KEY_APP_BACKGROUNDED_TIMESTAMP) }

    override fun isCurrentlySelected(clearWhatOption: ClearWhatOption): Boolean {
        val currentlySelected = automaticallyClearWhatSavedValue() ?: return false
        return currentlySelected == clearWhatOption
    }

    override fun isCurrentlySelected(clearWhenOption: ClearWhenOption): Boolean {
        val currentlySelected = automaticallyClearWhenSavedValue() ?: return false
        return currentlySelected == clearWhenOption
    }

    override fun isCurrentlySelected(fireAnimation: FireAnimation): Boolean {
        return selectedFireAnimationSavedValue() == fireAnimation
    }

    override var notifyMeInDownloadsDismissed: Boolean
        get() = preferences.getBoolean(KEY_NOTIFY_ME_IN_DOWNLOADS_DISMISSED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_NOTIFY_ME_IN_DOWNLOADS_DISMISSED, enabled) }

    private fun automaticallyClearWhatSavedValue(): ClearWhatOption? {
        val savedValue = preferences.getString(KEY_AUTOMATICALLY_CLEAR_WHAT_OPTION, null) ?: return null
        return ClearWhatOption.valueOf(savedValue)
    }

    private fun automaticallyClearWhenSavedValue(): ClearWhenOption? {
        val savedValue = preferences.getString(KEY_AUTOMATICALLY_CLEAR_WHEN_OPTION, null) ?: return null
        return ClearWhenOption.valueOf(savedValue)
    }

    private fun selectedFireAnimationSavedValue(): FireAnimation {
        val selectedFireAnimationSavedValue = preferences.getString(KEY_SELECTED_FIRE_ANIMATION, null)
        return fireAnimationMapper.fireAnimationFrom(selectedFireAnimationSavedValue, FireAnimation.HeroFire)
    }

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    private fun defaultIcon(): AppIcon {
        return if (appBuildConfig.isDebug) {
            AppIcon.BLUE
        } else {
            AppIcon.DEFAULT
        }
    }

    override var experimentalWebsiteDarkMode: Boolean
        get() = preferences.getBoolean(KEY_EXPERIMENTAL_SITE_DARK_MODE, false)
        set(enabled) = preferences.edit { putBoolean(KEY_EXPERIMENTAL_SITE_DARK_MODE, enabled) }

    companion object {
        const val FILENAME = "com.duckduckgo.app.settings_activity.settings"
        const val KEY_BACKGROUND_JOB_ID = "BACKGROUND_JOB_ID"
        const val KEY_AUTOCOMPLETE_ENABLED = "AUTOCOMPLETE_ENABLED"
        const val KEY_MALICIOUS_SITE_PROTECTION_ENABLED = "MALICIOUS_SITE_PROTECTION_ENABLED"
        const val KEY_AUTOMATIC_FIREPROOF_SETTING = "KEY_AUTOMATIC_FIREPROOF_SETTING"
        const val KEY_AUTOMATICALLY_CLEAR_WHAT_OPTION = "AUTOMATICALLY_CLEAR_WHAT_OPTION"
        const val KEY_AUTOMATICALLY_CLEAR_WHEN_OPTION = "AUTOMATICALLY_CLEAR_WHEN_OPTION"
        const val KEY_APP_BACKGROUNDED_TIMESTAMP = "APP_BACKGROUNDED_TIMESTAMP"
        const val KEY_APP_NOTIFICATIONS_ENABLED = "APP_NOTIFCATIONS_ENABLED"
        const val KEY_APP_USED_SINCE_LAST_CLEAR = "APP_USED_SINCE_LAST_CLEAR"
        const val KEY_HIDE_TIPS = "HIDE_TIPS"
        const val KEY_APP_ICON = "APP_ICON"
        const val KEY_SELECTED_FIRE_ANIMATION = "SELECTED_FIRE_ANIMATION"
        const val KEY_APP_ICON_CHANGED = "APP_ICON_CHANGED"
        const val KEY_SITE_LOCATION_PERMISSION_ENABLED = "KEY_SITE_LOCATION_PERMISSION_ENABLED"
        const val KEY_SYSTEM_LOCATION_PERMISSION_DENIED_FOREVER = "KEY_SYSTEM_LOCATION_PERMISSION_DENIED_FOREVER"
        const val KEY_SITE_LOCATION_PERMISSION_MIGRATED = "KEY_SITE_LOCATION_PERMISSION_MIGRATED"
        const val KEY_DO_NOT_SELL_ENABLED = "KEY_DO_NOT_SELL_ENABLED"
        const val APP_LINKS_ENABLED = "APP_LINKS_ENABLED"
        const val SHOW_APP_LINKS_PROMPT = "SHOW_APP_LINKS_PROMPT"
        const val SHOW_AUTOMATIC_FIREPROOF_DIALOG = "SHOW_AUTOMATIC_FIREPROOF_DIALOG"
        const val KEY_NOTIFY_ME_IN_DOWNLOADS_DISMISSED = "KEY_NOTIFY_ME_IN_DOWNLOADS_DISMISSED"
        const val KEY_EXPERIMENTAL_SITE_DARK_MODE = "KEY_EXPERIMENTAL_SITE_DARK_MODE"
        const val KEY_OMNIBAR_POSITION = "KEY_OMNIBAR_POSITION"
        const val KEY_IS_FULL_URL_ENABLED = "KEY_IS_FULL_URL_ENABLED"
    }

    private class FireAnimationPrefsMapper {
        companion object {
            private const val HERO_FIRE_PREFS_VALUE = "HERO_FIRE"
            private const val HERO_WATER_PREFS_VALUE = "HERO_WATER"
            private const val HERO_ABSTRACT_PREFS_VALUE = "HERO_ABSTRACT"
            private const val NONE_PREFS_VALUE = "NONE"
        }

        fun prefValue(fireAnimation: FireAnimation) = when (fireAnimation) {
            FireAnimation.HeroFire -> HERO_FIRE_PREFS_VALUE
            FireAnimation.HeroWater -> HERO_WATER_PREFS_VALUE
            FireAnimation.HeroAbstract -> HERO_ABSTRACT_PREFS_VALUE
            FireAnimation.None -> NONE_PREFS_VALUE
        }

        fun fireAnimationFrom(
            value: String?,
            defValue: FireAnimation,
        ) = when (value) {
            HERO_FIRE_PREFS_VALUE -> FireAnimation.HeroFire
            HERO_WATER_PREFS_VALUE -> FireAnimation.HeroWater
            HERO_ABSTRACT_PREFS_VALUE -> FireAnimation.HeroAbstract
            NONE_PREFS_VALUE -> FireAnimation.None
            else -> defValue
        }
    }

    class LoginDetectorPrefsMapper {
        fun mapToAutomaticFireproofSetting(oldLoginDetectorValue: Boolean): AutomaticFireproofSetting {
            return when (oldLoginDetectorValue) {
                false -> NEVER
                else -> ASK_EVERY_TIME
            }
        }
    }
}
