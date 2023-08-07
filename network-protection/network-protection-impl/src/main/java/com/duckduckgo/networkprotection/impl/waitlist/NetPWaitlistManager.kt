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

package com.duckduckgo.networkprotection.impl.waitlist

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState
import com.duckduckgo.networkprotection.impl.configuration.NetPRedeemCodeError
import com.duckduckgo.networkprotection.impl.configuration.NetPRedeemCodeRequest
import com.duckduckgo.networkprotection.impl.configuration.WgVpnControllerService
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import retrofit2.HttpException

interface NetPWaitlistManager {
    fun getState(): Flow<NetPWaitlistState>

    suspend fun getStateSync(): NetPWaitlistState

    suspend fun joinWaitlist()
    suspend fun upsertState(): NetPWaitlistState
    suspend fun redeemCode(inviteCode: String): RedeemCodeResult
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealNetPWaitlistManager @Inject constructor(
    private val service: WgVpnControllerService,
    private val repository: NetPWaitlistRepository,
    private val networkProtectionWaitlist: NetworkProtectionWaitlist,
    private val netPWaitlistService: NetPWaitlistService,
    private val dispatcherProvider: DispatcherProvider,
) : NetPWaitlistManager {

    private val state: MutableStateFlow<NetPWaitlistState> =
        MutableStateFlow(networkProtectionWaitlist.getState())

    override fun getState(): Flow<NetPWaitlistState> {
        return state.asStateFlow()
    }

    override suspend fun getStateSync(): NetPWaitlistState = withContext(dispatcherProvider.io()) {
        return@withContext state.value
    }

    override suspend fun joinWaitlist() = withContext(dispatcherProvider.io()) {
        val (token, timestamp) = netPWaitlistService.joinWaitlist()
        token?.let { repository.setWaitlistToken(it) }
        timestamp?.let { repository.setWaitlistTimestamp(it) }

        updateState()
        return@withContext
    }

    override suspend fun upsertState(): NetPWaitlistState = withContext(dispatcherProvider.io()) {
        runCatching {
            val frontier = netPWaitlistService.waitlistStatus().timestamp
            if (frontier > repository.getWaitlistTimestamp()) {
                repository.getWaitlistToken()?.let { token ->
                    val inviteCode = netPWaitlistService.getCode(token).code
                    redeemCode(inviteCode)
                }
            }
        }

        updateState()
        state.value
    }

    override suspend fun redeemCode(inviteCode: String): RedeemCodeResult {
        return try {
            val response = service.redeemCode(NetPRedeemCodeRequest(inviteCode))
            repository.setAuthenticationToken(response.token)
            updateState()
            RedeemCodeResult.Redeemed
        } catch (e: HttpException) {
            parseRedeemCodeError(e)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { e.asLog() }
            RedeemCodeResult.Failure
        }
    }

    private fun parseRedeemCodeError(e: HttpException): RedeemCodeResult {
        logcat(LogPriority.ERROR) { "WAITLIST REDEEM ERROR" }
        return try {
            val error = Moshi.Builder().build().adapter(NetPRedeemCodeError::class.java).fromJson(e.response()?.errorBody()?.string().orEmpty())
            when (error?.message) {
                NetPRedeemCodeError.INVALID -> RedeemCodeResult.InvalidCode
                else -> RedeemCodeResult.Failure
            }
        } catch (e: Exception) {
            RedeemCodeResult.InvalidCode
        }
    }

    private suspend fun updateState() {
        state.emit(networkProtectionWaitlist.getState())
    }
}

sealed class RedeemCodeResult {
    object Redeemed : RedeemCodeResult()
    object InvalidCode : RedeemCodeResult()
    object Failure : RedeemCodeResult()
}
