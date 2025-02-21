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

package com.duckduckgo.autofill.impl.service.mapper.fakes

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.service.mapper.AppCredentialProvider

class FakeAppCredentialsProvider constructor(
    private val credentials: List<LoginCredentials>,
) : AppCredentialProvider {
    override suspend fun getCredentials(appPackage: String): List<LoginCredentials> {
        return credentials.filter { it.domain!!.equals(appPackage) }
    }
}
