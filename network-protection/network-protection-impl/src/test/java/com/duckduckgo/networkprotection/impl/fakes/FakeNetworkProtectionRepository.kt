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

package com.duckduckgo.networkprotection.impl.fakes

import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.ServerDetails

class FakeNetworkProtectionRepository : NetworkProtectionRepository {
    private var _serverDetails: ServerDetails? = null
    private var _disabledDueToInvalidSubscription: Boolean = false

    override var privateKey: String?
        get() = null
        set(_) {}

    override val lastPrivateKeyUpdateTimeInMillis: Long
        get() = -1L

    override var enabledTimeInMillis: Long
        get() = -1L
        set(_) {}
    override var serverDetails: ServerDetails?
        get() = _serverDetails
        set(value) {
            _serverDetails = value
        }
    override var clientInterface: NetworkProtectionRepository.ClientInterface?
        get() = null
        set(_) {}

    override var disabledDueToAccessRevoked: Boolean
        get() = _disabledDueToInvalidSubscription
        set(value) {
            _disabledDueToInvalidSubscription = value
        }
}
