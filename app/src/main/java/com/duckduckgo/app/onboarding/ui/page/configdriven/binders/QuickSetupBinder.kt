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

package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.view.View
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignReinstallerQuickSetupBinding
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentInteraction
import com.duckduckgo.app.onboarding.ui.page.configdriven.QuickSetupContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The address-bar-position row is always shown: only the search-options row and the divider above it follow
 * `hideAddressBarRow`, matching the legacy screen.
 */
class QuickSetupBinder(
    private val binding: IncludeBrandDesignReinstallerQuickSetupBinding,
) : StatefulDialogBinder<ContentConfig.QuickSetup, QuickSetupContentState> {

    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.QuickSetup,
        state: MutableStateFlow<QuickSetupContentState>,
        scope: BindScope,
    ): ContentHandle = with(binding) {
        val context = root.context

        setDefaultBrowserItem.isVisible = !content.hideSetDefaultBrowserRow
        setDefaultBrowserDivider.isVisible = !content.hideSetDefaultBrowserRow
        addWidgetItem.isVisible = !content.hideAddWidgetRow
        addWidgetDivider.isVisible = !content.hideAddWidgetRow
        addressBarSearchOptionsItem.isVisible = !content.hideAddressBarRow
        addressBarSearchOptionsDivider.isVisible = !content.hideAddressBarRow

        setDefaultBrowserItem.setOnCheckedChangeListener { checked ->
            scope.execute(ContentInteraction.SetDefaultBrowserToggled(checked))
        }
        addWidgetItem.setOnCheckedChangeListener { checked ->
            scope.execute(ContentInteraction.AddWidgetToggled(checked))
        }
        addressBarPositionItem.setOnClickListener { scope.execute(ContentInteraction.EditAddressBarPosition) }
        addressBarSearchOptionsItem.setOnClickListener { scope.execute(ContentInteraction.EditSearchOptions) }

        scope.coroutineScope.launch {
            state.collect { render(it) }
        }

        quickSetupTitle.setTitle(content.title.resolve(context))

        ContentHandle(
            title = quickSetupTitle,
            fadeTargets = listOf(quickSetupOptionsContainer),
            result = { NewUserOnboardingEvent.QuickSetupConfirmed(state.value.addressBarPosition, state.value.withAi) },
            unbind = {
                setDefaultBrowserItem.setOnCheckedChangeListener {}
                addWidgetItem.setOnCheckedChangeListener {}
                addressBarPositionItem.setOnClickListener(null)
                addressBarSearchOptionsItem.setOnClickListener(null)
            },
        )
    }

    /** Switches render silently so re-rendering a collected value never re-fires the listener that produced it. */
    private fun render(state: QuickSetupContentState) = with(binding) {
        setDefaultBrowserItem.setCheckedSilently(state.defaultBrowserChecked)
        addWidgetItem.setCheckedSilently(state.widgetChecked)
        addressBarPositionItem.setIcon(addressBarPositionIconRes(state.addressBarPosition))
        addressBarPositionItem.setSecondaryText(addressBarPositionLabelRes(state.addressBarPosition))
        addressBarSearchOptionsItem.setIcon(searchOptionsIconRes(state.withAi))
        addressBarSearchOptionsItem.setSecondaryText(searchOptionsLabelRes(state.withAi))
    }

    private fun addressBarPositionIconRes(type: OmnibarType): Int = when (type) {
        OmnibarType.SINGLE_TOP -> R.drawable.ic_address_bar_top_24
        OmnibarType.SINGLE_BOTTOM -> R.drawable.ic_address_bar_bottom_24
        OmnibarType.SPLIT -> R.drawable.ic_address_bar_split_24
    }

    private fun addressBarPositionLabelRes(type: OmnibarType): Int = when (type) {
        OmnibarType.SINGLE_TOP -> R.string.preOnboardingAddressBarPositionTop
        OmnibarType.SINGLE_BOTTOM -> R.string.preOnboardingAddressBarPositionBottom
        OmnibarType.SPLIT -> R.string.preOnboardingAddressBarPositionSplit
    }

    private fun searchOptionsIconRes(withAi: Boolean): Int = if (withAi) R.drawable.ic_ai_24 else R.drawable.ic_search_24

    private fun searchOptionsLabelRes(withAi: Boolean): Int =
        if (withAi) R.string.quickSetupInputScreenSearchAndDuckAi else R.string.quickSetupInputScreenSearchOnly
}
