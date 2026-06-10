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

package com.duckduckgo.app.browser.menu

import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VpnMenuStore {
    var vpnMenuNotSubscribedShownCount: Int

    fun incrementVpnMenuShownCount()
    fun canShowVpnMenuForNotSubscribed(): Boolean
}

@ContributesBinding(AppScope::class)
class RealVpnMenuStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : VpnMenuStore {

    private val preferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME)
    }

    override var vpnMenuNotSubscribedShownCount: Int
        get() = preferences.getInt(KEY_VPN_MENU_NOT_SUBSCRIBED_SHOWN_COUNT, 0)
        set(value) {
            preferences.edit {
                putInt(KEY_VPN_MENU_NOT_SUBSCRIBED_SHOWN_COUNT, value)
            }
        }

    override fun incrementVpnMenuShownCount() {
        val currentCount = vpnMenuNotSubscribedShownCount
        vpnMenuNotSubscribedShownCount = currentCount + 1
    }

    override fun canShowVpnMenuForNotSubscribed(): Boolean {
        return vpnMenuNotSubscribedShownCount < MAX_VPN_MENU_SHOWN_COUNT
    }

    companion object {
        const val FILENAME = "com.duckduckgo.app.browser.menu.vpn"
        const val KEY_VPN_MENU_NOT_SUBSCRIBED_SHOWN_COUNT = "VPN_MENU_NOT_SUBSCRIBED_SHOWN_COUNT"
        const val MAX_VPN_MENU_SHOWN_COUNT = 4
    }
}
