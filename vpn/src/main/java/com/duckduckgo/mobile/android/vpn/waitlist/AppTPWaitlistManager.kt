/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.waitlist

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.waitlist.api.AppTPRedeemCodeError
import com.duckduckgo.mobile.android.vpn.waitlist.api.AppTrackingProtectionWaitlistService
import com.duckduckgo.mobile.android.vpn.waitlist.store.AtpWaitlistStateRepository
import com.duckduckgo.mobile.android.vpn.waitlist.store.WaitlistState
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject

interface AppTPWaitlistManager {
    suspend fun fetchInviteCode(): FetchCodeResult
    fun notifyOnJoinedWaitlist()
    fun joinWaitlist(
        timestamp: Int,
        token: String
    )

    suspend fun redeemCode(inviteCode: String): RedeemCodeResult
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidAppTPWaitlistManager @Inject constructor(
    private val service: AppTrackingProtectionWaitlistService,
    private val dataStore: AppTrackingProtectionWaitlistDataStore,
    private val repository: AtpWaitlistStateRepository,
    private val dispatcherProvider: DispatcherProvider
) : AppTPWaitlistManager {

    override suspend fun fetchInviteCode(): FetchCodeResult {
        val token = dataStore.waitlistToken
        val timestamp = dataStore.waitlistTimestamp
        if (repository.getState() == WaitlistState.InBeta) return FetchCodeResult.CodeExisted
        return withContext(dispatcherProvider.io()) {
            try {
                val waitlistTimestamp = service.waitlistStatus().timestamp
                if (waitlistTimestamp >= timestamp && token != null) {
                    val inviteCode = service.getCode(token).code
                    if (inviteCode.isNotEmpty()) {
                        dataStore.inviteCode = inviteCode
                        service.redeemCode(inviteCode)
                        return@withContext FetchCodeResult.Code
                    }
                }
                return@withContext FetchCodeResult.NoCode
            } catch (e: Exception) {
                return@withContext FetchCodeResult.NoCode
            }
        }
    }

    override fun notifyOnJoinedWaitlist() {
        dataStore.sendNotification = true
    }

    override fun joinWaitlist(
        timestamp: Int,
        token: String
    ) {
        if (dataStore.waitlistTimestamp == -1) {
            dataStore.waitlistTimestamp = timestamp
        }
        if (dataStore.waitlistToken == null) {
            dataStore.waitlistToken = token
        }
    }

    override suspend fun redeemCode(inviteCode: String): RedeemCodeResult {
        return withContext(dispatcherProvider.io()) {

            val result = try {
                service.redeemCode(inviteCode)
                storeInviteCode(inviteCode)
                RedeemCodeResult.Redeemed
            } catch (e: HttpException) {
                parseRedeemCodeError(e)
            } catch (e: Exception) {
                Timber.d(e.toString())
                RedeemCodeResult.Failure
            }
            return@withContext result
        }
    }

    private fun parseRedeemCodeError(e: HttpException): RedeemCodeResult {
        return try {
            val error = Moshi.Builder().build().adapter(AppTPRedeemCodeError::class.java).fromJson(e.response()?.errorBody()?.string().orEmpty())
            when (error?.error) {
                AppTPRedeemCodeError.INVALID -> RedeemCodeResult.InvalidCode
                AppTPRedeemCodeError.ALREADY_REDEEMED -> RedeemCodeResult.AlreadyRedeemed
                else -> RedeemCodeResult.Failure
            }
        } catch (e: Exception) {
            RedeemCodeResult.InvalidCode
        }
    }

    private fun storeInviteCode(inviteCode: String) {
        dataStore.inviteCode = inviteCode
    }
}

sealed class FetchCodeResult {
    object Code : FetchCodeResult()
    object NoCode : FetchCodeResult()
    object CodeExisted : FetchCodeResult()
}

sealed class RedeemCodeResult {
    object Redeemed : RedeemCodeResult()
    object InvalidCode : RedeemCodeResult()
    object AlreadyRedeemed : RedeemCodeResult()
    object Failure : RedeemCodeResult()
}
