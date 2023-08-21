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

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.state.NetPFeatureRemover
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface NetPWaitlistRepository {
    fun getAuthenticationToken(): String?
    fun setAuthenticationToken(authToken: String)
    suspend fun getWaitlistToken(): String?
    suspend fun setWaitlistToken(token: String)
    suspend fun getWaitlistTimestamp(): Int
    suspend fun setWaitlistTimestamp(timestamp: Int)
    fun acceptWaitlistTerms()
    fun didAcceptWaitlistTerms(): Boolean
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = NetPWaitlistRepository::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = NetPFeatureRemover.NetPStoreRemovalPlugin::class,
)
class RealNetPWaitlistRepository @Inject constructor(
    private val dataStore: NetPWaitlistDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : NetPWaitlistRepository, NetPFeatureRemover.NetPStoreRemovalPlugin {

    override fun getAuthenticationToken(): String? = dataStore.authToken

    override fun setAuthenticationToken(authToken: String) {
        dataStore.authToken = authToken
    }

    override suspend fun getWaitlistToken(): String? = withContext(dispatcherProvider.io()) {
        return@withContext dataStore.waitlistToken
    }

    override suspend fun setWaitlistToken(token: String) = withContext(dispatcherProvider.io()) {
        dataStore.waitlistToken = token
    }

    override suspend fun getWaitlistTimestamp(): Int = withContext(dispatcherProvider.io()) {
        return@withContext dataStore.waitlistTimestamp
    }

    override suspend fun setWaitlistTimestamp(timestamp: Int) = withContext(dispatcherProvider.io()) {
        dataStore.waitlistTimestamp = timestamp
    }

    override fun acceptWaitlistTerms() {
        dataStore.didAcceptedTerms = true
    }

    override fun didAcceptWaitlistTerms(): Boolean {
        return dataStore.didAcceptedTerms
    }

    override fun clearStore() {
        dataStore.clear()
    }
}
