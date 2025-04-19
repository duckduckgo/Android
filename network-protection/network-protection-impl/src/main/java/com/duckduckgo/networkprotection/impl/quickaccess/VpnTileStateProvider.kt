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

package com.duckduckgo.networkprotection.impl.quickaccess

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionAccessState
import com.duckduckgo.networkprotection.api.NetworkProtectionAccessState.NetPAccessState.UnLocked
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.quickaccess.VpnTileStateProvider.VpnTileState
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VpnTileStateProvider {
    suspend fun getVpnTileState(): VpnTileState

    enum class VpnTileState {
        CONNECTED,
        DISCONNECTED,
        UNAVAILABLE,
    }
}

@ContributesBinding(AppScope::class)
class RealVpnTileStateProvider @Inject constructor(
    private val netpAccessState: NetworkProtectionAccessState,
    private val networkProtectionState: NetworkProtectionState,
) : VpnTileStateProvider {
    override suspend fun getVpnTileState(): VpnTileState {
        return netpAccessState.getState().run {
            if (this !is UnLocked) {
                VpnTileState.UNAVAILABLE
            } else {
                if (networkProtectionState.isRunning()) {
                    VpnTileState.CONNECTED
                } else {
                    VpnTileState.DISCONNECTED
                }
            }
        }
    }
}
