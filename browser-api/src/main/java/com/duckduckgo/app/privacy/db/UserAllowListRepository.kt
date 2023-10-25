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

package com.duckduckgo.app.privacy.db

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface UserAllowListRepository {
    fun isUrlInUserAllowList(url: String): Boolean
    fun isUriInUserAllowList(uri: Uri): Boolean
    fun isDomainInUserAllowList(domain: String?): Boolean
    fun domainsInUserAllowList(): List<String>
    fun domainsInUserAllowListFlow(): Flow<List<String>>
    suspend fun addDomainToUserAllowList(domain: String)
    suspend fun removeDomainFromUserAllowList(domain: String)
}
