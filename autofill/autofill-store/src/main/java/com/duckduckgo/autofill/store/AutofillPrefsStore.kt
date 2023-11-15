/*
 * Copyright (c) 2022 DuckDuckGo
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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.autofill.store.feature.AutofillDefaultStateDecider
import timber.log.Timber

interface AutofillPrefsStore {
    var isEnabled: Boolean
    var autofillDeclineCount: Int
    var monitorDeclineCounts: Boolean
    var hasEverBeenPromptedToSaveLogin: Boolean

    /**
     * Returns if Autofill was enabled by default.
     *
     * Since it's possible for the user to manually enable/disable Autofill, we need a separate mechanism to determine if it was enabled by default
     * which is separate from its current state.
     */
    fun wasDefaultStateEnabled(): Boolean
}

class RealAutofillPrefsStore(
    private val applicationContext: Context,
    private val defaultStateDecider: AutofillDefaultStateDecider,
) : AutofillPrefsStore {

    private val prefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

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
            Timber.i("yyy Updated default state for Autofill; originally enabled: %s", true)
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

    companion object {
        const val FILENAME = "com.duckduckgo.autofill.store.autofill_store"
        const val AUTOFILL_ENABLED = "autofill_enabled"
        const val HAS_EVER_BEEN_PROMPTED_TO_SAVE_LOGIN = "autofill_has_ever_been_prompted_to_save_login"
        const val AUTOFILL_DECLINE_COUNT = "autofill_decline_count"
        const val MONITOR_AUTOFILL_DECLINES = "monitor_autofill_declines"
        const val ORIGINAL_AUTOFILL_DEFAULT_STATE_ENABLED = "original_autofill_default_state_enabled"
    }
}
