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

package com.duckduckgo.app.fakes

import android.net.Uri
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class UserAllowListRepositoryFake : UserAllowListRepository {
    override fun isUrlInUserAllowList(url: String): Boolean = false

    override fun isUriInUserAllowList(uri: Uri): Boolean = false

    override fun isDomainInUserAllowList(domain: String?): Boolean = false

    override fun domainsInUserAllowList(): List<String> = emptyList()

    override fun domainsInUserAllowListFlow(): Flow<List<String>> = flowOf(emptyList())

    override suspend fun addDomainToUserAllowList(domain: String) = Unit

    override suspend fun removeDomainFromUserAllowList(domain: String) = Unit
}
