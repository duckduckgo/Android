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
import javax.inject.Inject


class OnboardingSharedPreferences @Inject constructor(private val context: Context) : OnboardingStore {

    companion object {
        val name = "com.duckduckgo.app.onboarding.settings"
        val currentVersion = 1
        val onboardingVersion = "com.duckduckgo.app.onboarding.currentVersion"
    }

    override val shouldShow: Boolean
        get() = preferences.getInt(onboardingVersion, 0) < currentVersion

    override fun onboardingShown() {
        val editor = preferences.edit()
        editor.putInt(onboardingVersion, currentVersion)
        editor.apply()
    }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(name, Context.MODE_PRIVATE)
}
