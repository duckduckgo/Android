/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import android.view.View
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.databinding.PreOnboardingDaxDialogCtaBrandDesignUpdateBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.AddToDockBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.AddressBarBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.ComparisonChartBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.InputScreenBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.InputScreenPreviewBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.QuickSetupBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.WelcomeBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.WidgetPromptBinder

/**
 * Single dispatcher the engine calls into for every screen. Routes a [ContentConfig] to the one binder that
 * knows how to render it, and hands back the bound include's root view alongside the [ContentHandle] the
 * engine needs to drive title typing, fades, entrance animators, and CTA submission.
 *
 * Stateless screens ([DialogBinder]) only need [BindScope]. Stateful screens ([StatefulDialogBinder]) also need
 * their live [ContentValueStore]-backed [kotlinx.coroutines.flow.MutableStateFlow], seeded from
 * [Stateful.initialState] the first time that [ContentConfig] subtype is bound and surviving rebinds/rotation
 * with the VM (see [ContentValueStore]).
 *
 * Binding class name and the eight include-property names below are verified against
 * `pre_onboarding_dax_dialog_cta_brand_design_update.xml` — legacy addresses the same generated binding as
 * `binding.daxDialogCta` (e.g. `binding.daxDialogCta.welcomeContent`, `binding.daxDialogCta.addressBarContent`).
 */
class ContentBinder(
    binding: PreOnboardingDaxDialogCtaBrandDesignUpdateBinding,
    private val contentValues: ContentValueStore,
    isLightMode: () -> Boolean,
) {
    private val welcome = WelcomeBinder(binding.welcomeContent)
    private val comparisonChart = ComparisonChartBinder(binding.comparisonChartContent)
    private val addToDock = AddToDockBinder(binding.addToDockContent)
    private val widgetPrompt = WidgetPromptBinder(binding.widgetPromptContent)
    private val addressBar = AddressBarBinder(binding.addressBarContent, isLightMode)
    private val inputScreen = InputScreenBinder(binding.inputScreenContent, isLightMode)
    private val inputScreenPreview = InputScreenPreviewBinder(binding.inputScreenPreviewContent)
    private val quickSetup = QuickSetupBinder(binding.reinstallerQuickSetupContent)

    private val allContentViews = listOf(
        welcome.view,
        comparisonChart.view,
        addToDock.view,
        widgetPrompt.view,
        addressBar.view,
        inputScreen.view,
        inputScreenPreview.view,
        quickSetup.view,
    )

    /**
     * Fresh-stage reset: the welcome include defaults *visible* in the shared card XML (legacy's welcome
     * branches rely on that default, so it can't be flipped to gone), while the engine only ever hides the
     * include it previously bound. A fresh engine rendering any non-welcome screen first (mid-flow re-entry)
     * would otherwise keep the welcome include stacked above it in the card — its alpha-0 children reserving
     * blank height. Legacy's equivalent is each branch explicitly hiding every other include.
     */
    fun hideAll() {
        allContentViews.forEach { it.isVisible = false }
    }

    fun bind(content: ContentConfig, scope: BindScope): BoundContent = when (content) {
        is ContentConfig.Welcome -> BoundContent(welcome.view, welcome.bind(content, scope))
        is ContentConfig.ComparisonChart -> BoundContent(comparisonChart.view, comparisonChart.bind(content, scope))
        is ContentConfig.AddToDock -> BoundContent(addToDock.view, addToDock.bind(content, scope))
        is ContentConfig.WidgetPrompt -> BoundContent(widgetPrompt.view, widgetPrompt.bind(content, scope))
        is ContentConfig.AddressBar -> BoundContent(addressBar.view, addressBar.bind(content, contentValues.contentState(content), scope))
        is ContentConfig.InputScreen -> BoundContent(inputScreen.view, inputScreen.bind(content, contentValues.contentState(content), scope))
        is ContentConfig.InputScreenPreview ->
            BoundContent(inputScreenPreview.view, inputScreenPreview.bind(content, contentValues.contentState(content), scope))
        is ContentConfig.QuickSetup -> BoundContent(quickSetup.view, quickSetup.bind(content, contentValues.contentState(content), scope))
    }
}

/** What [ContentBinder.bind] hands back to the engine: the bound include's root view, and its [ContentHandle]. */
data class BoundContent(
    val view: View,
    val handle: ContentHandle,
)
