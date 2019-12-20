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

package com.duckduckgo.app.onboarding.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject


class OnboardingSharedPreferences @Inject constructor(private val context: Context) : OnboardingStore {

    override val shouldShow: Boolean
        get() = preferences.getInt(KEY_VERSION, 0) < CURRENT_VERSION

    override var onboardingDialogJourney: String?
        get() = preferences.getString(ONBOARDING_JOURNEY, null)
        set(dialogJourney) = preferences.edit { putString(ONBOARDING_JOURNEY, dialogJourney) }

    override fun onboardingShown() {
        val editor = preferences.edit()
        editor.putInt(KEY_VERSION, CURRENT_VERSION)
        editor.apply()
    }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)


    companion object {
        const val FILENAME = "com.duckduckgo.app.onboarding.settings"
        const val KEY_VERSION = "com.duckduckgo.app.onboarding.currentVersion"
        const val CURRENT_VERSION = 1
        const val ONBOARDING_JOURNEY = "onboardingJourney"
    }

}
