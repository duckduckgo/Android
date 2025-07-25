/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.store

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.autofill.store.feature.AutofillDefaultStateDecider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import logcat.LogPriority.INFO
import logcat.logcat

interface AutofillPrefsStore {
    var isEnabled: Boolean
    var autofillDeclineCount: Int
    var monitorDeclineCounts: Boolean
    var hasEverBeenPromptedToSaveLogin: Boolean
    var hasEverImportedPasswords: Boolean
    fun hasEverImportedPasswordsFlow(): Flow<Boolean>
    var hasDeclinedPasswordManagementImportPromo: Boolean
    var hasDeclinedInBrowserPasswordImportPromo: Boolean
    var hasDismissedMainAppSettingsPromo: Boolean
    val autofillStateSetByUser: Boolean
    var timestampUserLastPromptedToDisableAutofill: Long?
    var domainTargetDatasetVersion: Long
    var inBrowserImportPromoShownCount: Int

    /**
     * Returns if Autofill was enabled by default.
     *
     * Since it's possible for the user to manually enable/disable Autofill, we need a separate mechanism to determine if it was enabled by default
     * which is separate from its current state.
     */
    fun wasDefaultStateEnabled(): Boolean

    /**
     * Resets all values to their default state
     * Only for internal use
     */
    fun resetAllValues()
}

@SuppressLint("DenyListedApi")
class RealAutofillPrefsStore(
    private val applicationContext: Context,
    private val defaultStateDecider: AutofillDefaultStateDecider,
) : AutofillPrefsStore {

    private val prefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

    private val hasEverImportedPasswordsFlow = MutableSharedFlow<Boolean>(replay = 1)

    override var isEnabled: Boolean
        get(): Boolean {
            // if autofill state has been manually set by user, honor that
            if (autofillStateSetByUser()) {
                return prefs.getBoolean(AUTOFILL_ENABLED, false)
            }

            // if previously determined enabled by default was true, use that
            if (wasDefaultStateEnabled()) {
                return true
            }

            // otherwise, determine now what the default autofill state should be
            return defaultStateDecider.defaultState().also {
                storeDefaultValue(it)
            }
        }
        set(value) = prefs.edit {
            putBoolean(AUTOFILL_ENABLED, value)
        }

    override var hasEverBeenPromptedToSaveLogin: Boolean
        get() = prefs.getBoolean(HAS_EVER_BEEN_PROMPTED_TO_SAVE_LOGIN, false)
        set(value) = prefs.edit { putBoolean(HAS_EVER_BEEN_PROMPTED_TO_SAVE_LOGIN, value) }

    override var hasEverImportedPasswords: Boolean
        get() = prefs.getBoolean(HAS_EVER_IMPORT_PASSWORDS, false)
        set(value) {
            prefs.edit { putBoolean(HAS_EVER_IMPORT_PASSWORDS, value) }
            hasEverImportedPasswordsFlow.tryEmit(value)
        }

    override fun hasEverImportedPasswordsFlow(): Flow<Boolean> {
        return hasEverImportedPasswordsFlow
            .onStart { emit(hasEverImportedPasswords) }
            .distinctUntilChanged()
    }

    override var hasDeclinedPasswordManagementImportPromo: Boolean
        get() = prefs.getBoolean(HAS_DECLINED_PASSWORD_MANAGEMENT_IMPORT_PASSWORDS_PROMO, false)
        set(value) {
            prefs.edit { putBoolean(HAS_DECLINED_PASSWORD_MANAGEMENT_IMPORT_PASSWORDS_PROMO, value) }
        }

    override var hasDeclinedInBrowserPasswordImportPromo: Boolean
        get() = prefs.getBoolean(HAS_DECLINED_IN_BROWSER_IMPORT_PASSWORDS_PROMO, false)
        set(value) {
            prefs.edit { putBoolean(HAS_DECLINED_IN_BROWSER_IMPORT_PASSWORDS_PROMO, value) }
        }

    override var hasDismissedMainAppSettingsPromo: Boolean
        get() = prefs.getBoolean(HAS_DISMISSED_MAIN_APP_SETTINGS_PROMO, false)
        set(value) {
            prefs.edit { putBoolean(HAS_DISMISSED_MAIN_APP_SETTINGS_PROMO, value) }
        }

    override val autofillStateSetByUser: Boolean
        get() = autofillStateSetByUser()

    override var timestampUserLastPromptedToDisableAutofill: Long?
        get() {
            val timestamp = prefs.getLong(TIMESTAMP_WHEN_USER_LAST_PROMPTED_TO_DISABLE_AUTOFILL, -1)
            if (timestamp == -1L) {
                return null
            }
            return timestamp
        }
        set(value) = prefs.edit { putLong(TIMESTAMP_WHEN_USER_LAST_PROMPTED_TO_DISABLE_AUTOFILL, value ?: -1) }

    override var domainTargetDatasetVersion: Long
        get() = prefs.getLong(DOMAIN_TARGET_DATASET_VERSION, 0L)
        set(value) {
            prefs.edit {
                putLong(DOMAIN_TARGET_DATASET_VERSION, value)
            }
        }
    override var inBrowserImportPromoShownCount: Int
        get() = prefs.getInt(BROWSER_IMPORT_PROMO_SHOWN_COUNT, 0)
        set(value) {
            prefs.edit {
                putInt(BROWSER_IMPORT_PROMO_SHOWN_COUNT, value)
            }
        }

    /**
     * Returns if Autofill was enabled by default. Note, this is not necessarily the same as the current state of Autofill.
     */
    override fun wasDefaultStateEnabled(): Boolean {
        return prefs.getBoolean(ORIGINAL_AUTOFILL_DEFAULT_STATE_ENABLED, false)
    }

    /**
     * We need to later know if Autofill was enabled or disabled by default, so we store the original state here.
     *
     * It is safe to call this function multiple times. Internally, it will decide if it needs to store the value or not.
     *
     * @param enabledByDefault The value to store
     */
    private fun storeDefaultValue(
        enabledByDefault: Boolean,
    ) {
        if (enabledByDefault) {
            prefs.edit { putBoolean(ORIGINAL_AUTOFILL_DEFAULT_STATE_ENABLED, true) }
            logcat(INFO) { "Updated default state for Autofill; originally enabled: $enabledByDefault" }
        }
    }

    private fun autofillStateSetByUser(): Boolean {
        return prefs.contains(AUTOFILL_ENABLED)
    }

    override var autofillDeclineCount: Int
        get() = prefs.getInt(AUTOFILL_DECLINE_COUNT, 0)
        set(value) = prefs.edit { putInt(AUTOFILL_DECLINE_COUNT, value) }

    override var monitorDeclineCounts: Boolean
        get() = prefs.getBoolean(MONITOR_AUTOFILL_DECLINES, true)
        set(value) = prefs.edit { putBoolean(MONITOR_AUTOFILL_DECLINES, value) }

    override fun resetAllValues() {
        prefs.edit {
            remove(HAS_EVER_BEEN_PROMPTED_TO_SAVE_LOGIN)
            remove(AUTOFILL_DECLINE_COUNT)
            remove(MONITOR_AUTOFILL_DECLINES)
            remove(ORIGINAL_AUTOFILL_DEFAULT_STATE_ENABLED)
        }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.autofill.store.autofill_store"
        const val AUTOFILL_ENABLED = "autofill_enabled"
        const val HAS_EVER_BEEN_PROMPTED_TO_SAVE_LOGIN = "autofill_has_ever_been_prompted_to_save_login"
        const val HAS_EVER_IMPORT_PASSWORDS = "autofill_has_ever_import_passwords"
        const val HAS_DECLINED_IN_BROWSER_IMPORT_PASSWORDS_PROMO = "autofill_declined_in_browser_import_passwords_promo"
        const val HAS_DECLINED_PASSWORD_MANAGEMENT_IMPORT_PASSWORDS_PROMO = "autofill_dismissed_import_passwords_promo"
        const val BROWSER_IMPORT_PROMO_SHOWN_COUNT = "autofill_in_browser_import_promo_shown_count"
        const val HAS_DISMISSED_MAIN_APP_SETTINGS_PROMO = "autofill_dismissed_main_app_settings_import_passwords_promo"
        const val TIMESTAMP_WHEN_USER_LAST_PROMPTED_TO_DISABLE_AUTOFILL = "timestamp_when_user_last_prompted_to_disable_autofill"
        const val AUTOFILL_DECLINE_COUNT = "autofill_decline_count"
        const val MONITOR_AUTOFILL_DECLINES = "monitor_autofill_declines"
        const val ORIGINAL_AUTOFILL_DEFAULT_STATE_ENABLED = "original_autofill_default_state_enabled"
        const val DOMAIN_TARGET_DATASET_VERSION = "domain_target_dataset_version"
    }
}
