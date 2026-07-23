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
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogTitleController
import com.duckduckgo.app.onboarding.ui.page.configdriven.QuickSetupContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Stateful. Ported from BrandDesignUpdateWelcomePage:
 *  - row visibility: updateQuickSetupRowsVisibility :2317-2326
 *  - listeners: setQuickSetupListeners :2329-2355 (bottom-sheet/system-settings side effects are VM-owned;
 *    this binder only forwards intent via [ContentInteraction] / [scope]-execute, per the "binders never call
 *    the VM directly" rule — everything past that point, e.g. actually launching a bottom sheet, is Task 6+)
 *  - selection display: observeQuickSetupSelection/bindQuickSetupSelection :2357-2374, icon/label helpers
 *    :2376-2400
 *
 * Quirk carried over verbatim from legacy, not "fixed" here: [updateQuickSetupRowsVisibility] never toggles
 * `addressBarPositionItem` itself — only `hideSetDefaultBrowserRow`/`hideAddWidgetRow` gate their own row+divider,
 * and `hideAddressBarRow` only gates the *search options* row (`addressBarSearchOptionsItem`) and the divider
 * immediately above it. The address-bar-position row is always shown.
 *
 * State-down-events-up: switches use [QuickSetupSwitchRow.setCheckedSilently] for the state-down direction so
 * re-rendering the collected state never re-fires the listener that produced it. Edit rows have no view state
 * of their own to guard (they're one-shot clicks), so they route straight to [scope]`.execute`.
 *
 * Documented POC simplification: the quick-setup title string contains a literal `<br/>` (see
 * `preOnboardingReinstallQuickSetupTitle`) that legacy only resolves via `.html()` on its *snap* path
 * (:2156-2160) — its typing path (:986-987 hidden-twin aside) feeds the same raw string into
 * `startOnboardingTypingAnimation`, i.e. the typing path has the same literal-`<br/>` gap [DialogTitleController]
 * has here. Since [DialogTitleController.set] only accepts a plain `String` (no Spanned/HTML support) and no
 * other binder in this set applies `.html()` to a title, this follows the same convention rather than
 * special-casing one screen; flagged for whoever wires the real title view.
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

        setDefaultBrowserItem.setCheckedSilently(state.value.defaultBrowserChecked)
        addWidgetItem.setCheckedSilently(state.value.widgetChecked)
        bindSelection(state.value.addressBarPosition, state.value.withAi)

        setDefaultBrowserItem.setOnCheckedChangeListener { checked ->
            scope.execute(ContentInteraction.SetDefaultBrowserToggled(checked))
        }
        addWidgetItem.setOnCheckedChangeListener { checked ->
            scope.execute(ContentInteraction.AddWidgetToggled(checked))
        }
        addressBarPositionItem.setOnClickListener { scope.execute(ContentInteraction.EditAddressBarPosition) }
        addressBarSearchOptionsItem.setOnClickListener { scope.execute(ContentInteraction.EditSearchOptions) }

        scope.coroutineScope.launch {
            state.collect {
                setDefaultBrowserItem.setCheckedSilently(it.defaultBrowserChecked)
                addWidgetItem.setCheckedSilently(it.widgetChecked)
                bindSelection(it.addressBarPosition, it.withAi)
            }
        }

        val title = DialogTitleController(quickSetupTitle, quickSetupTitleHidden)
        title.set(content.title.resolve(context))

        ContentHandle(
            title = title,
            fadeTargets = listOf(quickSetupOptionsContainer),
            result = { NewUserOnboardingEvent.QuickSetupConfirmed(state.value.addressBarPosition, state.value.withAi) },
        )
    }

    private fun bindSelection(
        position: OmnibarType,
        withAi: Boolean,
    ) = with(binding) {
        addressBarPositionItem.setIcon(addressBarPositionIconRes(position))
        addressBarPositionItem.setSecondaryText(addressBarPositionLabelRes(position))
        addressBarSearchOptionsItem.setIcon(searchOptionsIconRes(withAi))
        addressBarSearchOptionsItem.setSecondaryText(searchOptionsLabelRes(withAi))
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

    private fun searchOptionsIconRes(withAi: Boolean): Int =
        if (withAi) R.drawable.ic_ai_24 else R.drawable.ic_search_24

    private fun searchOptionsLabelRes(withAi: Boolean): Int =
        if (withAi) R.string.quickSetupInputScreenSearchAndDuckAi else R.string.quickSetupInputScreenSearchOnly
}
