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

package com.duckduckgo.app.onboarding.ui.page.configdriven.engine

import android.view.View
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.databinding.PreOnboardingDaxDialogCtaBrandDesignUpdateBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentValueStore
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.AddressBarBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.ComparisonChartBinder
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
    private val binding: PreOnboardingDaxDialogCtaBrandDesignUpdateBinding,
    private val contentValues: ContentValueStore,
    isLightMode: () -> Boolean,
) : ContentController {

    private val comparisonChart = ComparisonChartBinder(binding.comparisonChartContent)
    private val addressBar = AddressBarBinder(binding.addressBarContent, isLightMode)

    private var boundView: View? = null

    /**
     * Covers every content include, not only the ones with a binder: some default to visible in the card
     * layout, so a first render of any other screen would otherwise leave one stacked above it, reserving
     * blank height inside the card.
     */
    override fun resetStage() {
        listOf(
            binding.welcomeContent.root,
            binding.comparisonChartContent.root,
            binding.addressBarContent.root,
            binding.inputScreenContent.root,
            binding.inputScreenPreviewContent.root,
            binding.reinstallerQuickSetupContent.root,
            binding.addToDockContent.root,
            binding.widgetPromptContent.root,
        ).forEach { it.isVisible = false }
    }

    override fun bind(
        stepId: LinearOnboardingStepId,
        content: ContentConfig,
        scope: BindScope,
    ): ContentHandle {
        val handle = when (content) {
            is ContentConfig.ComparisonChart -> {
                boundView = comparisonChart.view
                comparisonChart.bind(content, scope)
            }
            is ContentConfig.AddressBar -> {
                boundView = addressBar.view
                addressBar.bind(content, contentValues.contentState(stepId, content), scope)
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
