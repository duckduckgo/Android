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

package com.duckduckgo.mobile.android.vpn.prefs

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import com.duckduckgo.di.scopes.AppScope
import com.frybits.harmony.getHarmonySharedPreferences
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VpnSharedPreferencesProvider {
    fun getSharedPreferences(name: String, multiprocess: Boolean = false): SharedPreferences
}

@ContributesBinding(AppScope::class)
class VpnSharedPreferencesProviderImpl @Inject constructor(
    private val context: Context
) : VpnSharedPreferencesProvider {
    override fun getSharedPreferences(name: String, multiprocess: Boolean): SharedPreferences {
        return if (multiprocess) {
            context.getHarmonySharedPreferences(name)
        } else {
            context.getSharedPreferences(name, MODE_PRIVATE)
        }
    }
}
