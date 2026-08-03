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
import androidx.core.view.children
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.databinding.PreOnboardingDaxDialogCtaBrandDesignUpdateBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentValueStore
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.AddToDockBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.AddressBarBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.ComparisonChartBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.WelcomeBinder
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
    private val welcome = WelcomeBinder(binding.welcomeContent)
    private val addToDock = AddToDockBinder(binding.addToDockContent)

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
            is ContentConfig.AddToDock -> {
                boundView = addToDock.view
                addToDock.bind(content, scope)
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
