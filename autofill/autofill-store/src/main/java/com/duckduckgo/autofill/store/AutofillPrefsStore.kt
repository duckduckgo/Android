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
import com.duckduckgo.autofill.api.InternalTestUserChecker

interface AutofillPrefsStore {
    var isEnabled: Boolean
    var autofillDeclineCount: Int
    var monitorDeclineCounts: Boolean
    var hasEverBeenPromptedToSaveLogin: Boolean
}

class RealAutofillPrefsStore constructor(
    private val applicationContext: Context,
    private val internalTestUserChecker: InternalTestUserChecker,
) : AutofillPrefsStore {

    private val prefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

    override var isEnabled: Boolean
        get() = prefs.getBoolean(AUTOFILL_ENABLED, autofillEnabledDefaultValue())
        set(value) = prefs.edit {
            putBoolean(AUTOFILL_ENABLED, value)
        }

    override var hasEverBeenPromptedToSaveLogin: Boolean
        get() = prefs.getBoolean(HAS_EVER_BEEN_PROMPTED_TO_SAVE_LOGIN, false)
        set(value) = prefs.edit { putBoolean(HAS_EVER_BEEN_PROMPTED_TO_SAVE_LOGIN, value) }

    override var autofillDeclineCount: Int
        get() = prefs.getInt(AUTOFILL_DECLINE_COUNT, 0)
        set(value) = prefs.edit { putInt(AUTOFILL_DECLINE_COUNT, value) }

    override var monitorDeclineCounts: Boolean
        get() = prefs.getBoolean(MONITOR_AUTOFILL_DECLINES, true)
        set(value) = prefs.edit { putBoolean(MONITOR_AUTOFILL_DECLINES, value) }

    /**
     * Internal builds should have autofill enabled by default
     * It'll be disabled by default for public users, even after internal testing gate removed
     * If we decide to make it enabled by default for all users, we can hardcode the value to true
     */
    private fun autofillEnabledDefaultValue(): Boolean {
        return internalTestUserChecker.isInternalTestUser
    }

    companion object {
        const val FILENAME = "com.duckduckgo.autofill.store.autofill_store"
        const val AUTOFILL_ENABLED = "autofill_enabled"
        const val SHOW_SAVE_LOGIN_ONBOARDING = "autofill_show_onboardind_saved_login"
        const val HAS_EVER_BEEN_PROMPTED_TO_SAVE_LOGIN = "autofill_has_ever_been_prompted_to_save_login"
        const val AUTOFILL_DECLINE_COUNT = "autofill_decline_count"
        const val MONITOR_AUTOFILL_DECLINES = "monitor_autofill_declines"
    }
}
