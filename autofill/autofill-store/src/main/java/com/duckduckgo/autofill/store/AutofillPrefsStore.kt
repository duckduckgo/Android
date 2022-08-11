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

interface AutofillPrefsStore {
    var isEnabled: Boolean
    var showOnboardingWhenOfferingToSaveLogin: Boolean
}

class RealAutofillPrefsStore constructor(
    private val applicationContext: Context
) : AutofillPrefsStore {

    private val prefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

    override var isEnabled: Boolean
        get() = prefs.getBoolean(AUTOFILL_ENABLED, true)
        set(value) = prefs.edit {
            putBoolean(AUTOFILL_ENABLED, value)
        }

    override var showOnboardingWhenOfferingToSaveLogin: Boolean
        get() = prefs.getBoolean(SHOW_SAVE_LOGIN_ONBOARDING, true)
        set(value) = prefs.edit { putBoolean(SHOW_SAVE_LOGIN_ONBOARDING, value) }

    companion object {
        const val FILENAME = "com.duckduckgo.autofill.store.autofill_store"
        const val AUTOFILL_ENABLED = "autofill_enabled"
        const val SHOW_SAVE_LOGIN_ONBOARDING = "autofill_show_onboardind_saved_login"
    }
}
