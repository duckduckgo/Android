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

package com.duckduckgo.networkprotection.store

interface NetworkProtectionRepository {
    var privateKey: String?
}

class RealNetworkProtectionRepository constructor(
    private val networkProtectionPrefs: NetworkProtectionPrefs,
) : NetworkProtectionRepository {

    override var privateKey: String?
        get() = networkProtectionPrefs.getString(KEY_WG_PRIVATE_KEY, null)
        set(value) {
            value?.let {
                networkProtectionPrefs.putString(KEY_WG_PRIVATE_KEY, value)
            }
        }

    companion object {
        private const val KEY_WG_PRIVATE_KEY = "wg_private_key"
    }
}
