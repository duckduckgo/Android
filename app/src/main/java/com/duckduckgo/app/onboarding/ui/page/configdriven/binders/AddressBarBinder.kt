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
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignAddressBarPositionBinding
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.configdriven.AddressBarContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddressBarBinder(
    private val binding: IncludeBrandDesignAddressBarPositionBinding,
    private val isLightMode: () -> Boolean,
) : StatefulDialogBinder<ContentConfig.AddressBar, AddressBarContentState> {

    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.AddressBar,
        state: MutableStateFlow<AddressBarContentState>,
        scope: BindScope,
    ): ContentHandle = with(binding) {
        addressBarPicker.setLightMode(isLightMode())
        addressBarPicker.isSplitOptionVisible = content.showSplitOption
        addressBarPicker.setSelection(state.value.position, animate = false)
        addressBarPicker.setOnSelectionChangedListener { position -> state.update { it.copy(position = position) } }
        scope.coroutineScope.launch {
            state.collect { addressBarPicker.setSelection(it.position, animate = true) }
        }

        addressBarTitle.setTitle(content.title.resolve(root.context))

        ContentHandle(
            title = addressBarTitle,
            fadeTargets = listOf(addressBarPicker),
            result = { NewUserOnboardingEvent.AddressBarConfirmed(state.value.position) },
            unbind = { addressBarPicker.setOnSelectionChangedListener {} },
        )
    }
}
