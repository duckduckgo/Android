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

package com.duckduckgo.app.userwhitelist

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.userwhitelist.api.UserWhiteListRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class UserWhiteListAppRepository @Inject constructor(
    appDatabase: AppDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : UserWhiteListRepository {

    private val dao = appDatabase.userWhitelistDao()
    override val userWhiteList = CopyOnWriteArrayList<String>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            all().collect { list ->
                userWhiteList.clear()
                userWhiteList.addAll(list)
            }
        }
    }

    private fun all(): Flow<List<String>> {
        return dao.allFlow().map {
            userWhiteListedDomainList ->
            userWhiteListedDomainList.map {
                userWhiteListedDomain ->
                userWhiteListedDomain.domain
            }
        }
    }
}
