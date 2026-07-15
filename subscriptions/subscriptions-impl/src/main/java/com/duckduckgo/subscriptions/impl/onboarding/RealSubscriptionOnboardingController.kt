/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.onboarding

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController.Event
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * Relays step events to the host. AppScope so steps and the retained host ViewModel share one instance across
 * configuration changes. SharedFlow with no replay: buffered, not delivered to later runs.
 */
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSubscriptionOnboardingController @Inject constructor() : SubscriptionOnboardingController {

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 10)
    override val events: Flow<Event> = _events.asSharedFlow()

    override fun onStepFinished(stepId: String, outcome: SubscriptionOnboardingStepOutcome) {
        _events.tryEmit(Event.StepFinished(stepId, outcome))
    }

    override fun onBack() {
        _events.tryEmit(Event.Back)
    }

    override fun exitOnboarding() {
        _events.tryEmit(Event.Exit)
    }
}
