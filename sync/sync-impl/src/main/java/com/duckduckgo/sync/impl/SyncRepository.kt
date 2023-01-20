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
import com.duckduckgo.sync.lib.AccountKeys
import com.duckduckgo.sync.lib.SyncNativeLib
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

interface SyncRepository {
    fun createAccount(): Boolean
    fun getAccountInfo(): AccountInfo
    fun storeRecoveryCode()
    fun removeAccount()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class AppSyncRepository
@Inject
constructor(
    private val syncDeviceIds: SyncDeviceIds,
    private val nativeLib: SyncNativeLib,
    private val syncApi: SyncApi,
    private val syncStore: SyncStore,
) : SyncRepository {

    override fun createAccount(): Boolean {
        val account: AccountKeys = nativeLib.generateAccountKeys(userId = syncDeviceIds.userId())
        val result =
            syncApi.createAccount(
                account.userId,
                account.primaryKey,
                account.secretKey,
                account.passwordHash,
                account.protectedSecretKey,
                syncDeviceIds.deviceId(),
                syncDeviceIds.deviceName())

        return result is Result.Success
    }

    override fun getAccountInfo(): AccountInfo {
        if (!isSignedIn()) return AccountInfo()

        return AccountInfo(
            userId = syncDeviceIds.userId(),
            deviceName = syncDeviceIds.deviceName(),
            deviceId = syncDeviceIds.deviceId(),
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

    private fun isSignedIn() = !syncStore.primaryKey.isNullOrEmpty()

    class Adapters {
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
    val isSignedIn: Boolean = false
)

data class RecoveryCode(
    val primaryKey: String,
    val userID: String,
)
