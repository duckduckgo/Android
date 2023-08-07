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

package com.duckduckgo.networkprotection.impl.waitlist.store

class FakeNetPWaitlistRepository : NetPWaitlistRepository {
    private var token: String? = null
    private var waitlistToken: String? = null
    private var timestamp: Int = -1
    private var acceptedTerms: Boolean = false
    override fun getAuthenticationToken(): String? = token

    override fun setAuthenticationToken(authToken: String) {
        token = authToken
    }

    override suspend fun getWaitlistToken(): String? {
        return waitlistToken
    }

    override suspend fun setWaitlistToken(token: String) {
        waitlistToken = token
    }

    override suspend fun getWaitlistTimestamp(): Int {
        return timestamp
    }

    override suspend fun setWaitlistTimestamp(timestamp: Int) {
        this.timestamp = timestamp
    }

    override fun acceptWaitlistTerms() {
        acceptedTerms = true
    }

    override fun didAcceptWaitlistTerms(): Boolean {
        return this.acceptedTerms
    }
}
