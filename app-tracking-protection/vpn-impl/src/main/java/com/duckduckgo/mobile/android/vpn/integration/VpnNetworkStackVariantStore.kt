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

package com.duckduckgo.mobile.android.vpn.integration

import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VpnNetworkStackVariantStore {
    var variant: String?
}

@ContributesBinding(AppScope::class)
class VpnNetworkStackVariantStoreImpl @Inject constructor(
    sharedPreferencesProvider: VpnSharedPreferencesProvider
) : VpnNetworkStackVariantStore {

    private val preferences = sharedPreferencesProvider.getSharedPreferences(FILE_PREFERENCES, multiprocess = true, migrate = false)

    override var variant: String?
        @Synchronized get() = preferences.getString(NETWORK_LAYER, null)
        @Synchronized set(value) = preferences.edit { putString(NETWORK_LAYER, value) }
}

private const val FILE_PREFERENCES = "com.duckduckgo.mobile.android.vpn.network.stack.variant.v1"
private const val NETWORK_LAYER = "NETWORK_LAYER"
