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
import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.domain
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealUserAllowListRepository @Inject constructor(
    private val userAllowListDao: UserAllowListDao,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @IsMainProcess isMainProcess: Boolean,
) : UserAllowListRepository {

    private val userAllowList = CopyOnWriteArrayList<String>()
    override fun isUrlInUserAllowList(url: String): Boolean {
        return isUriInUserAllowList(url.toUri())
    }

    override fun isUriInUserAllowList(uri: Uri): Boolean {
        return isDomainInUserAllowList(uri.domain())
    }

    override fun isDomainInUserAllowList(domain: String?): Boolean {
        return userAllowList.contains(domain)
    }

    override fun domainsInUserAllowList(): List<String> {
        return userAllowList
    }

    override fun domainsInUserAllowListFlow(): Flow<List<String>> {
        return all()
            .onStart { emit(userAllowList.toList()) }
            .distinctUntilChanged()
    }

    override suspend fun addDomainToUserAllowList(domain: String) {
        withContext(dispatcherProvider.io()) {
            userAllowListDao.insert(domain)
        }
    }

    override suspend fun removeDomainFromUserAllowList(domain: String) {
        withContext(dispatcherProvider.io()) {
            userAllowListDao.delete(domain)
        }
    }

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                all().collect { list ->
                    userAllowList.clear()
                    userAllowList.addAll(list)
                }
            }
        }
    }

    private fun all(): Flow<List<String>> {
        return userAllowListDao.allDomainsFlow()
    }
}
