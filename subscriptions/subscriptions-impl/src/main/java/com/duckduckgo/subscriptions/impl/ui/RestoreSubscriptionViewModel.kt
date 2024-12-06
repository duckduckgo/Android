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

package com.duckduckgo.subscriptions.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.Companion.SUBSCRIPTION_NOT_FOUND_ERROR
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.RecoverSubscriptionResult
import com.duckduckgo.subscriptions.impl.SubscriptionsChecker
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ACTIVATE_URL_V1
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ACTIVATE_URL_V2
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.isExpired
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.Error
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.FinishAndGoToOnboarding
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.FinishAndGoToSubscriptionSettings
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.RestoreFromEmail
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.SubscriptionNotFound
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.Success
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesViewModel(ActivityScope::class)
class RestoreSubscriptionViewModel @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val subscriptionsChecker: SubscriptionsChecker,
    private val dispatcherProvider: DispatcherProvider,
    private val pixelSender: SubscriptionPixelSender,
    private val privacyProFeature: Lazy<PrivacyProFeature>,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private lateinit var subscriptionStatus: SubscriptionStatus

    fun init() {
        subscriptionsManager.subscriptionStatus
            .onEach { subscriptionStatus = it }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val newUiState = if (isActivationFlowV2()) {
                ActivationFlowUIState.LoadedV2
            } else {
                ActivationFlowUIState.LoadedV1
            }
            _viewState.update {
                it.copy(uiState = newUiState)
            }
        }
    }

    fun restoreFromStore() {
        pixelSender.reportActivateSubscriptionRestorePurchaseClick()
        viewModelScope.launch(dispatcherProvider.io()) {
            when (val subscription = subscriptionsManager.recoverSubscriptionFromStore()) {
                is RecoverSubscriptionResult.Success -> {
                    subscriptionsChecker.runChecker()
                    pixelSender.reportRestoreUsingStoreSuccess()
                    pixelSender.reportSubscriptionActivated()
                    command.send(Success)
                }

                is RecoverSubscriptionResult.Failure -> {
                    when (subscription.message) {
                        SUBSCRIPTION_NOT_FOUND_ERROR -> {
                            if (subscriptionStatus.isExpired()) {
                                subscriptionsManager.signOut()
                            }
                            pixelSender.reportRestoreUsingStoreFailureSubscriptionNotFound()
                            command.send(SubscriptionNotFound)
                        }

                        else -> {
                            pixelSender.reportRestoreUsingStoreFailureOther()
                            command.send(Error)
                        }
                    }
                }
            }
        }
    }

    fun restoreFromEmail() {
        pixelSender.reportActivateSubscriptionEnterEmailClick()
        viewModelScope.launch {
            val (url, webViewTitle) = if (isActivationFlowV2()) {
                ACTIVATE_URL_V2 to ActivationFlowWebViewTitle.Dax
            } else {
                ACTIVATE_URL_V1 to ActivationFlowWebViewTitle.AddEmail
            }

            command.send(
                RestoreFromEmail(
                    activationUrl = url,
                    webViewTitle = webViewTitle,
                ),
            )
        }
    }

    fun onSubscriptionRestoredFromEmail() = viewModelScope.launch {
        if (subscriptionStatus.isExpired()) {
            command.send(FinishAndGoToSubscriptionSettings)
        } else {
            command.send(FinishAndGoToOnboarding)
        }
    }

    data class ViewState(
        val uiState: ActivationFlowUIState = ActivationFlowUIState.Loading,
    )

    sealed class ActivationFlowUIState {
        data object Loading : ActivationFlowUIState()
        data object LoadedV1 : ActivationFlowUIState()
        data object LoadedV2 : ActivationFlowUIState()
    }

    sealed class ActivationFlowWebViewTitle {
        data object Dax : ActivationFlowWebViewTitle()
        data object AddEmail : ActivationFlowWebViewTitle()
    }

    sealed class Command {
        data class RestoreFromEmail(
            val activationUrl: String,
            val webViewTitle: ActivationFlowWebViewTitle,
        ) : Command()

        data object Success : Command()
        data object SubscriptionNotFound : Command()
        data object Error : Command()
        data object FinishAndGoToSubscriptionSettings : Command()
        data object FinishAndGoToOnboarding : Command()
    }

    private suspend fun isActivationFlowV2(): Boolean = withContext(dispatcherProvider.io()) {
        privacyProFeature.get().isActivationFlowV2().isEnabled()
    }
}
