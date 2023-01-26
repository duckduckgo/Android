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

package com.duckduckgo.sync.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.crypto.AccountKeys
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import timber.log.Timber

interface SyncRepository {
    fun createAccount(): Result<Boolean>
    fun getAccountInfo(): AccountInfo
    fun storeRecoveryCode()
    fun removeAccount()
    fun logout(): Result<Boolean>
    fun deleteAccount(): Result<Boolean>
    fun latestToken(): String
}

@ContributesBinding(AppScope::class)
class AppSyncRepository @Inject constructor(
    private val syncDeviceIds: SyncDeviceIds,
    private val nativeLib: SyncLib,
    private val syncApi: SyncApi,
    private val syncStore: SyncStore,
) : SyncRepository {

    override fun createAccount(): Result<Boolean> {
        val userId = syncDeviceIds.userId()
        val deviceId = syncDeviceIds.deviceId()
        val deviceName = syncDeviceIds.deviceName()

        val account: AccountKeys = nativeLib.generateAccountKeys(userId = userId)
        if (account.result != 0L) return Result.Error(code = account.result.toInt(), reason = "Account keys failed")

        val result =
            syncApi.createAccount(
                account.userId,
                account.passwordHash,
                account.protectedSecretKey,
                deviceId,
                deviceName,
            )

        return when (result) {
            is Result.Error -> {
                Timber.i("SYNC signup failed $result")
                result
            }

            is Result.Success -> {
                syncStore.userId = userId
                syncStore.deviceId = deviceId
                syncStore.deviceName = deviceName
                syncStore.token = result.data.token
                syncStore.primaryKey = account.primaryKey
                syncStore.secretKey = account.secretKey
                Result.Success(true)
            }
        }
    }

    override fun getAccountInfo(): AccountInfo {
        if (!isSignedIn()) return AccountInfo()

        return AccountInfo(
            userId = syncStore.userId.orEmpty(),
            deviceName = syncStore.deviceName.orEmpty(),
            deviceId = syncStore.deviceId.orEmpty(),
            isSignedIn = isSignedIn(),
        )
    }

    override fun storeRecoveryCode() {
        val primaryKey = syncStore.primaryKey ?: return
        val userID = syncStore.userId ?: return
        val recoveryCodeJson = Adapters.recoveryCodeAdapter.toJson(RecoveryCode(primaryKey, userID))

        Timber.i("SYNC store recoverCode: $recoveryCodeJson")
        syncStore.recoveryCode = recoveryCodeJson
    }

    override fun removeAccount() {
        syncStore.clearAll()
    }

    override fun logout(): Result<Boolean> {
        val token =
            syncStore.token.takeUnless { it.isNullOrEmpty() }
                ?: return Result.Error(reason = "Token Empty")
        val deviceId =
            syncStore.deviceId.takeUnless { it.isNullOrEmpty() }
                ?: return Result.Error(reason = "Device Id Empty")

        return when (val result = syncApi.logout(token, deviceId)) {
            is Result.Error -> {
                Timber.i("SYNC logout failed $result")
                result
            }

            is Result.Success -> {
                syncStore.clearAll()
                Result.Success(true)
            }
        }
    }

    override fun deleteAccount(): Result<Boolean> {
        val token =
            syncStore.token.takeUnless { it.isNullOrEmpty() }
                ?: return Result.Error(reason = "Token Empty")

        return when (val result = syncApi.deleteAccount(token)) {
            is Result.Error -> {
                Timber.i("SYNC deleteAccount failed $result")
                result
            }
            is Result.Success -> {
                syncStore.clearAll()
                Result.Success(true)
            }
        }
    }

    override fun latestToken(): String {
        return syncStore.token ?: ""
    }

    private fun isSignedIn() = !syncStore.primaryKey.isNullOrEmpty()

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val recoveryCodeAdapter: JsonAdapter<RecoveryCode> =
                moshi.adapter(RecoveryCode::class.java)
        }
    }
}

data class AccountInfo(
    val userId: String = "",
    val deviceName: String = "",
    val deviceId: String = "",
    val isSignedIn: Boolean = false,
)

data class RecoveryCode(
    val primaryKey: String,
    val userID: String,
)

sealed class Result<out R> {

    data class Success<out T>(val data: T) : Result<T>()
    data class Error(
        val code: Int = -1,
        val reason: String,
    ) : Result<Nothing>()

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[exception=$code, $reason]"
        }
    }
}
