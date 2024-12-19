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

package com.duckduckgo.networkprotection.impl.subscription.settings

import kotlinx.coroutines.flow.Flow

interface NetworkProtectionSettingsState {

    /**
     * Returns a flow of the visibility states of NetP
     * The caller DOES NOT need to specify the dispatcher when calling this method
     */
    suspend fun getNetPSettingsStateFlow(): Flow<NetPSettingsState>

    /**
     * If the Netp Settings Item should be visible to the user and it's current subscription state
     */
    sealed interface NetPSettingsState {

        sealed interface Visible : NetPSettingsState {
            data object Subscribed : Visible
            data object Expired : Visible
            data object Activating : Visible
        }
        data object Hidden : NetPSettingsState
    }
}
