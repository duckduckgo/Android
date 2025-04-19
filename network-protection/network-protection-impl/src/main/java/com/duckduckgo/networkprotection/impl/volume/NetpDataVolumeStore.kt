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

package com.duckduckgo.networkprotection.impl.volume

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.volume.NetpDataVolumeStore.DataVolume
import com.duckduckgo.networkprotection.store.NetworkProtectionPrefs
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface NetpDataVolumeStore {
    var dataVolume: DataVolume
    data class DataVolume(
        val receivedBytes: Long = 0L,
        val transmittedBytes: Long = 0L,
    )
}

@ContributesBinding(AppScope::class)
class RealNetpDataVolumeStore @Inject constructor(
    private val networkProtectionPrefs: NetworkProtectionPrefs,
) : NetpDataVolumeStore {
    override var dataVolume: DataVolume
        get() {
            return kotlin.runCatching {
                DataVolume(
                    receivedBytes = networkProtectionPrefs.getLong(KEY_RECEIVED_BYTES, 0L),
                    transmittedBytes = networkProtectionPrefs.getLong(KEY_TRANSMITTED_BYTES, 0L),
                )
            }.getOrDefault(DataVolume())
        }
        set(value) {
            kotlin.runCatching {
                networkProtectionPrefs.putLong(KEY_RECEIVED_BYTES, value.receivedBytes)
                networkProtectionPrefs.putLong(KEY_TRANSMITTED_BYTES, value.transmittedBytes)
            }
        }

    companion object {
        private const val KEY_RECEIVED_BYTES = "key_received_bytes"
        private const val KEY_TRANSMITTED_BYTES = "key_transmitted_bytes"
    }
}
