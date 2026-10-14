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

package com.duckduckgo.app.onboarding.ui.page.welcome.engine

import android.view.View
import androidx.core.view.children
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.databinding.PreOnboardingDaxDialogCtaBinding
import com.duckduckgo.app.onboarding.ui.page.welcome.BindScope
import com.duckduckgo.app.onboarding.ui.page.welcome.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.welcome.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.welcome.ContentValueStore
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.AddressBarBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.ComparisonChartBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.DownloadReasonBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.DuckAiStateBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.ImportCompleteBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.ImportPasswordsBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.InputScreenBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.InputScreenPreviewBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.PreferenceSelectorBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.QuickSetupBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.SingleChoiceBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.TogglePositionBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.WelcomeBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.WidgetPromptBinder
import com.duckduckgo.onboarding.api.LinearOnboardingStepId

interface ContentController {
    /** Hides every content include. Used before the first bind, when includes still sit at their XML defaults. */
    fun resetStage()

    fun bind(stepId: LinearOnboardingStepId, content: ContentConfig, scope: BindScope): ContentHandle

    fun hideBound()
}

/**
 * Routes a [ContentConfig] to the one binder that renders it, and owns which content include is on show.
 */
class ContentControllerImpl(
    private val binding: PreOnboardingDaxDialogCtaBinding,
    private val contentValues: ContentValueStore,
    private val onBeforeContentBound: (LinearOnboardingStepId, ContentConfig) -> Unit,
    isLightMode: () -> Boolean,
    isAddressBarRebrandEnabled: () -> Boolean,
) : ContentController {

    private val comparisonChart = ComparisonChartBinder(binding.comparisonChartContent)
    private val addressBar = AddressBarBinder(binding.addressBarContent, isLightMode)
    private val inputScreen = InputScreenBinder(binding.inputScreenContent, isLightMode)
    private val inputScreenPreview = InputScreenPreviewBinder(binding.inputScreenPreviewContent, isAddressBarRebrandEnabled)
    private val quickSetup = QuickSetupBinder(binding.reinstallerQuickSetupContent)
    private val welcome = WelcomeBinder(binding.welcomeContent)
    private val widgetPrompt = WidgetPromptBinder(binding.widgetPromptContent)
    private val importPasswords = ImportPasswordsBinder(binding.importPasswordsContent)
    private val importComplete = ImportCompleteBinder(binding.importCompleteContent)
    private val downloadReason = DownloadReasonBinder(binding.downloadReasonContent)
    private val preferenceSelector = PreferenceSelectorBinder(binding.preferenceSelectorContent)
    private val singleChoice = SingleChoiceBinder(binding.singleChoiceContent)
    private val togglePosition = TogglePositionBinder(binding.togglePositionContent, isLightMode)
    private val duckAiState = DuckAiStateBinder(binding.duckAiStateContent)

    private var boundView: View? = null

    /**
     * Covers every content include, not only the ones with a binder: `welcomeContent` defaults to visible in the
     * card layout, so a first render of any other screen would otherwise leave it stacked above, reserving blank
     * height inside the card. The CTAs share the container but belong to the card stage.
     */
    override fun resetStage() {
        binding.cardContainer.children
            .filter { it !== binding.primaryCta && it !== binding.secondaryCta }
            .forEach { it.isVisible = false }
    }

    override fun bind(
        stepId: LinearOnboardingStepId,
        content: ContentConfig,
        scope: BindScope,
    ): ContentHandle {
        onBeforeContentBound(stepId, content)
        val handle = when (content) {
            is ContentConfig.Welcome -> {
                boundView = welcome.view
                welcome.bind(content, scope)
            }
            is ContentConfig.ComparisonChart -> {
                boundView = comparisonChart.view
                comparisonChart.bind(content, scope)
            }
            is ContentConfig.AddressBar -> {
                boundView = addressBar.view
                addressBar.bind(content, contentValues.contentState(stepId, content), scope)
            }
            is ContentConfig.InputScreen -> {
                boundView = inputScreen.view
                inputScreen.bind(content, contentValues.contentState(stepId, content), scope)
            }
            is ContentConfig.InputScreenPreview -> {
                boundView = inputScreenPreview.view
                inputScreenPreview.bind(content, contentValues.contentState(stepId, content), scope)
            }
            is ContentConfig.QuickSetup -> {
                boundView = quickSetup.view
                quickSetup.bind(content, contentValues.contentState(stepId, content), scope)
            }
            is ContentConfig.WidgetPrompt -> {
                boundView = widgetPrompt.view
                widgetPrompt.bind(content, scope)
            }
            is ContentConfig.ImportPasswords -> {
                boundView = importPasswords.view
                importPasswords.bind(content, scope)
            }
            is ContentConfig.ImportComplete -> {
                boundView = importComplete.view
                importComplete.bind(content, contentValues.contentState(stepId, content), scope)
            }
            is ContentConfig.DownloadReason -> {
                boundView = downloadReason.view
                downloadReason.bind(content, contentValues.contentState(stepId, content), scope)
            }
            is ContentConfig.PreferenceSelector -> {
                boundView = preferenceSelector.view
                preferenceSelector.bind(content, contentValues.contentState(stepId, content), scope)
            }
            is ContentConfig.SingleChoice -> {
                boundView = singleChoice.view
                singleChoice.bind(content, contentValues.contentState(stepId, content), scope)
            }
            is ContentConfig.TogglePosition -> {
                boundView = togglePosition.view
                togglePosition.bind(content, scope)
            }
            is ContentConfig.DuckAiState -> {
                boundView = duckAiState.view
                duckAiState.bind(content, scope)
            }
        }
        boundView?.isVisible = true
        return handle
    }

    override fun hideBound() {
        boundView?.isVisible = false
        boundView = null
    }
}
