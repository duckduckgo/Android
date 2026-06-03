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

package com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.NewAddressBarOptionV2Prompt
import com.duckduckgo.duckchat.api.NewAddressBarOptionV2Prompt.Command
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import logcat.LogPriority.DEBUG
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class, boundType = ModalEvaluator::class)
@ContributesBinding(scope = AppScope::class, boundType = NewAddressBarOptionV2Prompt::class)
@SingleInstanceIn(AppScope::class)
class NewAddressBarOptionV2ModalEvaluator @Inject constructor(
    private val duckChat: DuckChat,
    private val duckChatFeature: DuckChatFeature,
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    private val dispatchers: DispatcherProvider,
) : ModalEvaluator, NewAddressBarOptionV2Prompt {

    override val priority: Int = 1

    override val evaluatorId: String = "new_address_bar_option_v2"

    private val _commands = Channel<Command>(capacity = Channel.CONFLATED)
    override val commands: Flow<Command> = _commands.receiveAsFlow()

    override suspend fun evaluate(): ModalEvaluator.EvaluationResult = withContext(dispatchers.io()) {
        if (isV2Enabled() && isDuckAiEnabled() && isInputScreenNeverEnabled() && hasNotShownBefore()) {
            // Persist before showing so a force-kill mid-display still counts as shown (once-per-install).
            duckChatFeatureRepository.setNewAddressBarOptionV2Shown()
            _commands.trySend(Command.ShowPicker)
            ModalEvaluator.EvaluationResult.ModalShown
        } else {
            ModalEvaluator.EvaluationResult.Skipped
        }
    }

    override suspend fun onConfirmed(searchAndAiSelected: Boolean) {
        if (searchAndAiSelected) {
            duckChat.setInputScreenUserSetting(true)
        }
    }

    private fun isV2Enabled(): Boolean =
        duckChatFeature.showAIChatAddressBarChoiceScreenV2().isEnabled().also {
            logcat(DEBUG) { "NewAddressBarOptionV2: $it isV2Enabled" }
        }

    private fun isDuckAiEnabled(): Boolean =
        duckChat.isEnabled().also {
            logcat(DEBUG) { "NewAddressBarOptionV2: $it isDuckAiEnabled" }
        }

    // "Ever enabled" is sticky and counts the onboarding choice — the TOGGLE_NEVER_ENABLED DAU cohort.
    private suspend fun isInputScreenNeverEnabled(): Boolean =
        (!duckChatFeatureRepository.isInputScreenEverEnabled()).also {
            logcat(DEBUG) { "NewAddressBarOptionV2: $it isInputScreenNeverEnabled" }
        }

    private suspend fun hasNotShownBefore(): Boolean =
        (!duckChatFeatureRepository.wasNewAddressBarOptionV2Shown()).also {
            logcat(DEBUG) { "NewAddressBarOptionV2: $it hasNotShownBefore" }
        }
}
