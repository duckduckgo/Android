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
import com.duckduckgo.mobile.android.vpn.waitlist.api.AppTPRedeemCodeError
import com.duckduckgo.mobile.android.vpn.waitlist.api.AppTrackingProtectionWaitlistService
import com.squareup.moshi.Moshi
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber

interface TrackingProtectionWaitlistManager {
    fun waitlistState(): WaitlistState
    suspend fun fetchInviteCode(): FetchCodeResult
    fun isFeatureEnabled(): Boolean
    fun notifyOnJoinedWaitlist()
    fun joinWaitlist(timestamp: Int, token: String)
    suspend fun redeemCode(inviteCode: String): RedeemCodeResult
}

class AppTrackingProtectionWaitlistManager(
    private val service: AppTrackingProtectionWaitlistService,
    private val dataStore: AppTrackingProtectionWaitlistDataStore,
    private val dispatcherProvider: DispatcherProvider
) : TrackingProtectionWaitlistManager {

    override fun waitlistState(): WaitlistState {
        if (dataStore.waitlistTimestamp != -1 && dataStore.inviteCode == null) {
            return WaitlistState.JoinedQueue(dataStore.sendNotification)
        }
        if (isFeatureEnabled()) {
            return WaitlistState.InBeta
        }
        return WaitlistState.NotJoinedQueue
    }

    override suspend fun fetchInviteCode(): FetchCodeResult {
        val token = dataStore.waitlistToken
        val timestamp = dataStore.waitlistTimestamp
        if (isFeatureEnabled()) return FetchCodeResult.CodeExisted
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

    // We only do this until the Waitlist is enabled
    override fun isFeatureEnabled(): Boolean = true
    // override fun isFeatureEnabled(): Boolean = dataStore.inviteCode != null

    override fun notifyOnJoinedWaitlist() {
        dataStore.sendNotification = true
    }

    override fun joinWaitlist(timestamp: Int, token: String) {
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
            val error = Moshi.Builder().build().adapter(AppTPRedeemCodeError::class.java).fromJson(e.response()?.errorBody()?.string())
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

sealed class WaitlistState {
    object NotJoinedQueue : WaitlistState()
    data class JoinedQueue(val notify: Boolean = false) : WaitlistState()
    object InBeta : WaitlistState()
    object CodeRedeemed : WaitlistState()
}
