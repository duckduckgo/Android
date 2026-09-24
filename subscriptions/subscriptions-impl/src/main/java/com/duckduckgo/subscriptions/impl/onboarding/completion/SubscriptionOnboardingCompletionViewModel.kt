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

package com.duckduckgo.subscriptions.impl.onboarding.completion

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.pir.api.PirFeature
import com.duckduckgo.pir.api.dashboard.PirFeatureState
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController.Event.StepFinished
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingHandoffState
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStepStore
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Summarises how far the user got through onboarding. The rows come from the step plugins themselves, so a
 * feature module owning a step also owns its label and icon here; PIR is appended separately because it has
 * no onboarding step of its own and is set up from this screen rather than in the linear flow.
 */
@ContributesViewModel(FragmentScope::class)
class SubscriptionOnboardingCompletionViewModel @Inject constructor(
    private val controller: SubscriptionOnboardingController,
    private val stepPlugins: PluginPoint<SubscriptionOnboardingStepPlugin>,
    private val stepStore: SubscriptionOnboardingStepStore,
    private val subscriptions: Subscriptions,
    private val pirFeature: PirFeature,
    private val handoffState: SubscriptionOnboardingHandoffState,
) : ViewModel() {

    data class ViewState(
        val rows: List<SummaryRow> = emptyList(),
        val completionPercentage: Int = 0,
        val handoff: Boolean = false,
        val celebratory: Boolean = false,
    )

    data class SummaryRow(
        val id: String,
        @StringRes val labelResId: Int,
        @DrawableRes val pendingIconResId: Int,
        val completed: Boolean,
        val clickable: Boolean,
    )

    sealed interface Command {
        data object LaunchPirStep : Command
    }

    private val viewState = MutableStateFlow(ViewState(handoff = handoffState.isHandoff))
    fun viewState(): Flow<ViewState> = viewState.asStateFlow()

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    // Cached so the summary can be rebuilt without waiting for a fresh entitlements emission.
    private var pirEntitled = false

    // PIR is completed from its own screen while this one is in the background. We learn it from the
    // controller event directly (not the step store) so we don't race the host writing that store.
    private var pirCompleted = false

    init {
        subscriptions.getEntitlementStatus()
            .onEach { products ->
                pirEntitled = Product.PIR in products
                rebuild()
            }
            .launchIn(viewModelScope)

        controller.events
            .onEach { event ->
                if (event is StepFinished && event.stepId == PIR_ROW_ID && event.outcome == COMPLETED) {
                    pirCompleted = true
                    rebuild()
                }
            }
            .launchIn(viewModelScope)
    }

    fun onDoneClicked() {
        controller.exitOnboarding()
    }

    fun onPirRowClicked() {
        _commands.trySend(Command.LaunchPirStep)
    }

    private fun rebuild() {
        viewModelScope.launch { viewState.value = buildViewState() }
    }

    private suspend fun buildViewState(): ViewState {
        val stepRows = stepPlugins.getPlugins()
            .filter { it.shouldShow() }
            .mapNotNull { plugin ->
                plugin.completionSummaryRow?.let { entry ->
                    SummaryRow(
                        id = plugin.stepId,
                        labelResId = entry.labelResId,
                        pendingIconResId = entry.pendingIconResId,
                        completed = stepStore.isCompleted(plugin.stepId),
                        clickable = false,
                    )
                }
            }

        // Only show the PIR row when the user both is entitled to it and is eligible (the feature is enabled
        // and available for them).
        val showPir = pirEntitled && pirFeature.getPirFeatureState() == PirFeatureState.ENABLED
        val rows = if (showPir) stepRows + pirRow() else stepRows
        val percentage = rows.completionPercentage()

        return ViewState(
            rows = rows,
            completionPercentage = percentage,
            handoff = handoffState.isHandoff,
            celebratory = !handoffState.isHandoff && percentage == 100,
        )
    }

    // PIR is set up from this screen (Activate → PIR flow), not in the linear onboarding. Its row is
    // completed once the user has started a scan, and stays tappable until then.
    private fun pirRow(): SummaryRow {
        val completed = pirCompleted || stepStore.isCompleted(PIR_ROW_ID)
        return SummaryRow(
            id = PIR_ROW_ID,
            labelResId = R.string.subscriptionOnboardingFeature4Title,
            pendingIconResId = R.drawable.identity_blocked_pir_grayscale_color_24,
            completed = completed,
            clickable = !completed,
        )
    }

    private fun List<SummaryRow>.completionPercentage(): Int =
        if (isEmpty()) 0 else count { it.completed } * 100 / size

    companion object {
        const val PIR_ROW_ID = "pir"
    }
}
