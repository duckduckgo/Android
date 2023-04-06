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

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import retrofit2.HttpException

interface NetPWaitlistManager {
    fun getState(): NetPWaitlistState
    fun getAuthenticationToken(): String?
    suspend fun redeemCode(inviteCode: String): RedeemCodeResult
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealNetPWaitlistManager @Inject constructor(
    private val service: NetPWaitlistService,
    private val repository: NetPWaitlistRepository,
    private val appBuildConfig: AppBuildConfig,
) : NetPWaitlistManager {

    override fun getState(): NetPWaitlistState = repository.getState(appBuildConfig.isInternalBuild())

    override fun getAuthenticationToken(): String? = repository.getAuthenticationToken()

    override suspend fun redeemCode(inviteCode: String): RedeemCodeResult {
        return try {
            val response = service.redeemCode(NetPRedeemCodeRequest(inviteCode))
            repository.setAuthenticationToken(response.token)
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
}

sealed class RedeemCodeResult {
    object Redeemed : RedeemCodeResult()
    object InvalidCode : RedeemCodeResult()
    object Failure : RedeemCodeResult()
}
