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

package com.duckduckgo.macos_impl.waitlist

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.Code
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.CodeExisted
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.NoCode
import com.duckduckgo.macos_impl.waitlist.api.MacOsWaitlistService
import com.duckduckgo.macos_store.MacOsWaitlistRepository
import com.duckduckgo.macos_store.MacOsWaitlistState
import com.duckduckgo.macos_store.MacOsWaitlistState.InBeta
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface MacOsWaitlistManager {
    suspend fun fetchInviteCode(): FetchCodeResult
    fun joinWaitlist(timestamp: Int, token: String)
    fun getState(): MacOsWaitlistState
    fun getInviteCode(): String?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealMacOsWaitlistManager @Inject constructor(
    private val service: MacOsWaitlistService,
    private val repository: MacOsWaitlistRepository,
    private val dispatcherProvider: DispatcherProvider
) : MacOsWaitlistManager {

    override suspend fun fetchInviteCode(): FetchCodeResult {
        val token = repository.getToken()
        val timestamp = repository.getTimestamp()
        if (repository.getState() is InBeta) return CodeExisted
        return withContext(dispatcherProvider.io()) {
            try {
                val waitlistTimestamp = service.waitlistStatus().timestamp
                if (waitlistTimestamp >= timestamp && token != null) {
                    val res = service.getCode(token)
                    val inviteCode = res.code
                    if (inviteCode.isNotEmpty()) {
                        repository.setInviteCode(inviteCode)
                        return@withContext Code
                    }
                }
                return@withContext NoCode
            } catch (e: Exception) {
                return@withContext NoCode
            }
        }
    }

    override fun joinWaitlist(timestamp: Int, token: String) {
        repository.joinWaitlist(timestamp, token)
    }

    override fun getState(): MacOsWaitlistState = repository.getState()

    override fun getInviteCode(): String? = repository.getInviteCode()
}

sealed class FetchCodeResult {
    object Code : FetchCodeResult()
    object NoCode : FetchCodeResult()
    object CodeExisted : FetchCodeResult()
}
