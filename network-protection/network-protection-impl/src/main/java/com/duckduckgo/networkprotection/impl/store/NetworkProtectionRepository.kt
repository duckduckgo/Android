/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.store

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.state.NetPFeatureRemover
import com.duckduckgo.networkprotection.store.NetworkProtectionPrefs
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

interface NetworkProtectionRepository {
    var enabledTimeInMillis: Long
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = NetworkProtectionRepository::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = NetPFeatureRemover.NetPStoreRemovalPlugin::class,
)
class RealNetworkProtectionRepository @Inject constructor(
    private val networkProtectionPrefs: NetworkProtectionPrefs,
) : NetworkProtectionRepository, NetPFeatureRemover.NetPStoreRemovalPlugin {

    override var enabledTimeInMillis: Long
        get() = networkProtectionPrefs.getLong(KEY_WG_SERVER_ENABLE_TIME, -1)
        set(value) {
            networkProtectionPrefs.putLong(KEY_WG_SERVER_ENABLE_TIME, value)
        }

    override fun clearStore() {
        networkProtectionPrefs.clear()
    }

    companion object {
        private const val KEY_WG_SERVER_ENABLE_TIME = "wg_server_enable_time"
    }
}
