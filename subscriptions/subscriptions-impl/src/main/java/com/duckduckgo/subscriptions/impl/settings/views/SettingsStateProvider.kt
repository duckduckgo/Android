/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.settings.views

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.settings.views.SettingsStateProvider.SettingsState
import com.duckduckgo.subscriptions.impl.settings.views.SettingsStateProvider.SettingsState.Empty
import com.duckduckgo.subscriptions.impl.settings.views.SettingsStateProvider.SettingsState.SubscriptionActivationInProgress
import com.duckduckgo.subscriptions.impl.settings.views.SettingsStateProvider.SettingsState.SubscriptionActive
import com.duckduckgo.subscriptions.impl.settings.views.SettingsStateProvider.SettingsState.SubscriptionOfferAvailable
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface SettingsStateProvider {

    fun getSettingsState(): Flow<SettingsState>

    sealed class SettingsState {
        data object Empty : SettingsState()
        data object SubscriptionOfferAvailable : SettingsState()
        data object SubscriptionActivationInProgress : SettingsState()
        data object SubscriptionActive : SettingsState()
    }
}

@SingleInstanceIn(ActivityScope::class)
@ContributesBinding(ActivityScope::class)
class RealSettingsStateProvider @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    subscriptionsManager: SubscriptionsManager,
) : SettingsStateProvider {

    private val state by lazy {
        subscriptionsManager.subscriptionStatus
            .map { status ->
                when (status) {
                    AUTO_RENEWABLE, NOT_AUTO_RENEWABLE, GRACE_PERIOD -> SubscriptionActive
                    WAITING -> SubscriptionActivationInProgress
                    else -> SubscriptionOfferAvailable
                }
            }
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = Empty,
            )
    }

    override fun getSettingsState(): Flow<SettingsState> = state
}
