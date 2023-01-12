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

package com.duckduckgo.sync.impl.ui

import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.SyncDeviceIds
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.lib.AccountKeys
import com.duckduckgo.sync.lib.SyncNativeLib
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart

@ContributesViewModel(ActivityScope::class)
class SyncInitialSetupViewModel
@Inject
constructor(
    private val syncDeviceIds: SyncDeviceIds,
    private val nativeLib: SyncNativeLib,
    private val syncApi: SyncApi,
) : ViewModel() {

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart { updateViewState() }

    data class ViewState(
        val userId: String = "",
        val deviceName: String = "",
        val deviceId: String = "",
    )

    private suspend fun updateViewState() {
        viewState.emit(
            viewState.value.copy(
                userId = syncDeviceIds.userId(),
                deviceName = syncDeviceIds.deviceName(),
                deviceId = syncDeviceIds.deviceId(),
            ),
        )
    }

    fun onCreateAccountClicked() {
        viewModelScope.launch(Dispatchers.IO) {
            val account: AccountKeys = nativeLib.generateAccountKeys(
                userId = syncDeviceIds.userId()
            )
            syncApi.createAccount(
                account.userId,
                account.primaryKey,
                account.secretKey,
                account.passwordHash,
                account.protectedSecretKey,
                syncDeviceIds.deviceId(),
                syncDeviceIds.deviceName())
        }
    }

    fun onStoreRecoveryCodeClicked() {
        viewModelScope.launch(Dispatchers.IO) {
            syncApi.storeRecoveryCode()
        }
    }

    fun onResetClicked() {
        viewModelScope.launch(Dispatchers.IO) {

        }
    }
}
