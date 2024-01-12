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

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.impl.Subscription
import com.duckduckgo.subscriptions.impl.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.FinishSignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToPortal
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Monthly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Yearly
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class SubscriptionSettingsViewModel @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()
    data class ViewState(
        val date: String? = null,
        val duration: SubscriptionDuration? = null,
        val status: SubscriptionStatus? = null,
        val platform: String? = null,
    )

    override fun onResume(owner: LifecycleOwner) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val subs = subscriptionsManager.getSubscription()
            if (subs is Subscription.Success) {
                val formatter = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                val date = formatter.format(Date(subs.expiresOrRenewsAt))
                val type = if (subs.productId == MONTHLY_PLAN) Monthly else Yearly
                _viewState.emit(viewState.value.copy(date = date, duration = type, status = subs.status, platform = subs.platform))
            }
        }
    }

    fun goToStripe() {
        viewModelScope.launch(dispatcherProvider.io()) {
            val url = subscriptionsManager.getPortalUrl() ?: return@launch
            command.send(GoToPortal(url))
        }
    }

    fun removeFromDevice() {
        viewModelScope.launch {
            subscriptionsManager.signOut()
            command.send(FinishSignOut)
        }
    }

    sealed class SubscriptionDuration {
        data object Monthly : SubscriptionDuration()
        data object Yearly : SubscriptionDuration()
    }

    sealed class Command {
        data object FinishSignOut : Command()
        data class GoToPortal(val url: String) : Command()
    }
}
