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

package com.duckduckgo.app.onboarding.ui.page.welcome

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogDefaults
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows
import com.duckduckgo.onboarding.api.LinearOnboardingStepId
import kotlin.time.Duration.Companion.milliseconds

internal class OnboardingContentState(
    val stepId: LinearOnboardingStepId,
    val config: ContentConfig,
    val values: ContentValueStore,
    private val execute: (ContentInteraction) -> Unit,
    val addressBarRebrandEnabled: Boolean,
    private val previewEditor: OnboardingPreviewEditor = OnboardingPreviewEditor(),
) {
    val inputEditor = previewEditor.field
    var readyAnimated by mutableStateOf<Boolean?>(null)
        private set
    var skipped by mutableStateOf(false)
        private set
    var disposed = false
        private set
    var displayedImportOutcome by mutableStateOf((config as? ContentConfig.ImportComplete)?.let { values.contentState(stepId, it).value })
        private set
    val importAlpha = Animatable(1f)
    var title by mutableStateOf(importTitle(displayedImportOutcome))
    var primaryEnabled by mutableStateOf(
        when (config) {
            is ContentConfig.DownloadReason -> values.contentState(stepId, config).value.selection != null
            is ContentConfig.ImportComplete -> displayedImportOutcome !is ImportCompleteContentState.Parsing
            else -> true
        },
    )
    var boundsDuration by mutableStateOf(DaxOnboardingDialogDefaults.BoundsAnimationDuration)

    fun resolvedTitle(context: Context): String = title.resolve(context).preventWidows().html(context).toString()

    fun ready(animated: Boolean) {
        if (!disposed && readyAnimated == null) readyAnimated = animated
    }

    fun skip() {
        skipped = true
    }

    fun interact(interaction: ContentInteraction) {
        if (!disposed && readyAnimated != null) execute(interaction)
    }

    fun <S : Any> stateFor(content: Stateful<S>) = values.contentState(stepId, content)

    fun submit(): NewUserOnboardingEvent? = when (val content = config) {
        is ContentConfig.AddressBar -> NewUserOnboardingEvent.AddressBarConfirmed(stateFor(content).value.position)
        is ContentConfig.InputScreen -> NewUserOnboardingEvent.InputModeConfirmed(stateFor(content).value.withAi)
        is ContentConfig.QuickSetup -> stateFor(content).value.let { NewUserOnboardingEvent.QuickSetupConfirmed(it.addressBarPosition, it.withAi) }
        is ContentConfig.DownloadReason -> NewUserOnboardingEvent.DownloadReasonConfirmed(stateFor(content).value.selection)
        is ContentConfig.PreferenceSelector -> NewUserOnboardingEvent.PreferenceSelectorConfirmed(stateFor(content).value.enabled)
        is ContentConfig.SingleChoice -> NewUserOnboardingEvent.SingleChoiceConfirmed(stateFor(content).value.selected)
        else -> null
    }

    fun presentImportOutcome(outcome: ImportCompleteContentState) {
        if (disposed) return
        displayedImportOutcome = outcome
        title = importTitle(outcome)
        primaryEnabled = outcome !is ImportCompleteContentState.Parsing
        boundsDuration = 300.milliseconds
    }

    private fun importTitle(outcome: ImportCompleteContentState?): TextConfig = when {
        config !is ContentConfig.ImportComplete -> config.title
        outcome is ImportCompleteContentState.Parsing -> config.parsingTitle
        outcome is ImportCompleteContentState.Failed -> config.failedTitle
        else -> config.title
    }

    fun dispose() {
        if (disposed) return
        if (config is ContentConfig.InputScreenPreview) previewEditor.retain(inputEditor)
        disposed = true
    }
}

internal class OnboardingPreviewEditor {
    var field = TextFieldState()
        private set

    fun retain(outgoing: TextFieldState) {
        // BasicTextField collapses its selection on focus loss; snapshot before the outgoing field is removed.
        field = TextFieldState(outgoing.text.toString(), outgoing.selection)
    }
}
