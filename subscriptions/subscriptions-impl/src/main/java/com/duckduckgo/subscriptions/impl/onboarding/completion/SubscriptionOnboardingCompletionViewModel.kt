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
 * no onboarding step of its own and can only be set up outside this flow.
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
        // When true this summary is a brief hand-off before a feature opens (e.g. Duck.ai): the view keeps
        // that feature's header and hides the action button, rather than showing the terminal summary.
        val handoff: Boolean = false,
    )

    data class SummaryRow(
        val id: String,
        @StringRes val labelResId: Int,
        @DrawableRes val pendingIconResId: Int,
        val completed: Boolean,
        val clickable: Boolean,
    )

    sealed interface Command {
        data object OpenPirDashboard : Command
        data object OpenPirDesktop : Command
        data object ShowPirUnavailableDialog : Command
    }

    private val viewState = MutableStateFlow(ViewState(handoff = handoffState.isHandoff))
    fun viewState(): Flow<ViewState> = viewState.asStateFlow()

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    init {
        subscriptions.getEntitlementStatus()
            .onEach { entitlements -> viewState.value = buildViewState(Product.PIR in entitlements) }
            .launchIn(viewModelScope)
    }

    fun onDoneClicked() {
        controller.exitOnboarding()
    }

    fun onPirRowClicked() {
        viewModelScope.launch {
            when (pirFeature.getPirFeatureState()) {
                PirFeatureState.ENABLED -> {
                    _commands.send(Command.OpenPirDashboard)
                    controller.exitOnboarding()
                }
                PirFeatureState.DISABLED -> {
                    _commands.send(Command.OpenPirDesktop)
                    controller.exitOnboarding()
                }
                // Staying put: there is nothing to hand the user off to, so onboarding shouldn't end here.
                PirFeatureState.NOT_AVAILABLE -> _commands.send(Command.ShowPirUnavailableDialog)
            }
        }
    }

    private suspend fun buildViewState(pirEntitled: Boolean): ViewState {
        val stepRows = stepPlugins.getPlugins()
            .filter { it.shouldShow() }
            .mapNotNull { plugin ->
                plugin.summaryEntry?.let { entry ->
                    SummaryRow(
                        id = plugin.stepId,
                        labelResId = entry.labelResId,
                        pendingIconResId = entry.pendingIconResId,
                        completed = stepStore.isCompleted(plugin.stepId),
                        clickable = false,
                    )
                }
            }

        val rows = if (pirEntitled) stepRows + pirRow() else stepRows

        return ViewState(
            rows = rows,
            completionPercentage = rows.completionPercentage(),
            handoff = handoffState.isHandoff,
        )
    }

    // PIR can only be set up outside onboarding, so its row is always outstanding and always drags the
    // percentage below 100 for an entitled user.
    private fun pirRow(): SummaryRow = SummaryRow(
        id = PIR_ROW_ID,
        labelResId = R.string.subscriptionOnboardingFeature4Title,
        pendingIconResId = R.drawable.identity_blocked_pir_grayscale_color_24,
        completed = false,
        clickable = true,
    )

    private fun List<SummaryRow>.completionPercentage(): Int =
        if (isEmpty()) 0 else count { it.completed } * 100 / size

    companion object {
        const val PIR_ROW_ID = "pir"
    }
}
