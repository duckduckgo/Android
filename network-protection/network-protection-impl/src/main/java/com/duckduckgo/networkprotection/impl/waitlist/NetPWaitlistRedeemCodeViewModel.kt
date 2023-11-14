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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistRedeemCodeViewModel.ViewState.Idle
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesViewModel(ActivityScope::class)
class NetPWaitlistRedeemCodeViewModel @Inject constructor(
    private val waitlistManager: NetPWaitlistManager,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val viewStateFlow: MutableStateFlow<ViewState> =
        MutableStateFlow(Idle)
    val viewState: StateFlow<ViewState> = viewStateFlow

    sealed class ViewState {
        object Idle : ViewState()
        object Redeeming : ViewState()
        object Redeemed : ViewState()
        object InvalidCode : ViewState()
        object ErrorRedeeming : ViewState()
    }

    fun redeemCode(inviteCode: String) {
        viewModelScope.launch(dispatcherProvider.io()) {
            withContext(dispatcherProvider.main()) {
                viewStateFlow.emit(ViewState.Redeeming)
            }
            val result = waitlistManager.redeemCode(inviteCode)
            val redeemState = when (result) {
                RedeemCodeResult.InvalidCode -> ViewState.InvalidCode
                RedeemCodeResult.Redeemed -> ViewState.Redeemed
                else -> ViewState.ErrorRedeeming
            }
            withContext(dispatcherProvider.main()) {
                viewStateFlow.emit(redeemState)
            }
        }
    }
}
