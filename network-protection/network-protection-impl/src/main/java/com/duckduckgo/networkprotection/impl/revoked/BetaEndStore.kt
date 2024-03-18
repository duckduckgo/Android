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

package com.duckduckgo.networkprotection.impl.revoked

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface BetaEndStore {
    fun showBetaEndDialog()
    fun betaEndDialogShown(): Boolean
}

@ContributesBinding(AppScope::class)
class RealBetaEndStore @Inject constructor(
    private val vpnSharedPreferencesProvider: VpnSharedPreferencesProvider,
) : BetaEndStore {
    private val preferences: SharedPreferences by lazy {
        vpnSharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = true,
            migrate = false,
        )
    }

    override fun showBetaEndDialog() {
        preferences.edit(commit = true) {
            putBoolean(KEY_END_DIALOG_SHOWN, true)
        }
    }

    override fun betaEndDialogShown(): Boolean = preferences.getBoolean(KEY_END_DIALOG_SHOWN, false)

    companion object {
        const val FILENAME = "com.duckduckgo.networkprotection.impl.waitlist.end.store.v1"
        const val KEY_END_DIALOG_SHOWN = "KEY_END_DIALOG_SHOWN"
    }
}
