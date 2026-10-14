/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.auth

import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Event
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Request
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Response
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDeviceAuthenticator : DeviceAuthenticator {
    var response: Response = Response.Allowed.UserAuthenticated
    var events: List<Event> = emptyList()

    private val _requests = mutableListOf<Request>()
    val requests: List<Request> get() = _requests.toList()

    override val currentPrompt = MutableStateFlow<AuthPrompt?>(null)

    override suspend fun authenticate(
        request: Request,
        onEvent: (Event) -> Unit,
    ): Response {
        _requests += request
        events.forEach(onEvent)
        return response
    }
}
