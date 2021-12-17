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

package com.duckduckgo.app.waitlist.trackerprotection.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.waitlist.RedeemCodeResult
import com.duckduckgo.mobile.android.vpn.waitlist.TrackingProtectionWaitlistManager
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppTPWaitlistRedeemCodeViewModel(
    private val waitlistManager: TrackingProtectionWaitlistManager,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val viewStateFlow: MutableStateFlow<ViewState> = MutableStateFlow(ViewState.Idle)
    val viewState: StateFlow<ViewState> = viewStateFlow

    sealed class ViewState {
        object Idle : ViewState()
        object Redeeming : ViewState()
        object Redeemed : ViewState()
        object InvalidCode : ViewState()
        object ErrorRedeeming : ViewState()
    }

    fun redeemCode(inviteCode: String) {
        viewModelScope.launch {
            viewStateFlow.emit(ViewState.Redeeming)
            val result =
                withContext(dispatcherProvider.io()) { waitlistManager.redeemCode(inviteCode) }
            val redeemState =
                when (result) {
                    RedeemCodeResult.InvalidCode -> ViewState.InvalidCode
                    RedeemCodeResult.Redeemed -> ViewState.Redeemed
                    RedeemCodeResult.AlreadyRedeemed -> ViewState.InvalidCode
                    else -> ViewState.ErrorRedeeming
                }
            viewStateFlow.emit(redeemState)
        }
    }
}

@ContributesMultibinding(AppScope::class)
class AppTPWaitlistRedeemCodeViewModelFactory
@Inject
constructor(
    private val waitlistManager: Provider<TrackingProtectionWaitlistManager>,
    private val dispatcherProvider: Provider<DispatcherProvider>,
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(AppTPWaitlistRedeemCodeViewModel::class.java) ->
                    (AppTPWaitlistRedeemCodeViewModel(
                        waitlistManager.get(), dispatcherProvider.get()) as
                        T)
                else -> null
            }
        }
    }
}
