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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignPreferenceRowBinding
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignPreferenceSelectorBinding
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.PreferenceSelectorContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import com.duckduckgo.mobile.android.R as CommonR

class PreferenceSelectorBinder(
    private val binding: IncludeBrandDesignPreferenceSelectorBinding,
) : StatefulDialogBinder<ContentConfig.PreferenceSelector, PreferenceSelectorContentState> {

    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.PreferenceSelector,
        state: MutableStateFlow<PreferenceSelectorContentState>,
        scope: BindScope,
    ): ContentHandle {
        val context = binding.root.context

        populateRows(content, state)

        binding.preferenceSelectorTitle.setTitle(content.title.resolve(context))

        return ContentHandle(
            title = binding.preferenceSelectorTitle,
            fadeTargets = listOf(binding.preferenceSelectorContentContainer),
            result = {
                NewUserOnboardingEvent.PreferenceSelectorConfirmed(state.value.enabled)
            },
            unbind = { binding.preferenceRows.removeAllViews() },
        )
    }

    /**
     * The switch is the only thing that writes to the state, so a row renders its value once at build time
     * rather than collecting: there is nothing else that could move it out from under the user.
     */
    private fun populateRows(
        content: ContentConfig.PreferenceSelector,
        state: MutableStateFlow<PreferenceSelectorContentState>,
    ) {
        val context = binding.root.context
        binding.preferenceRows.removeAllViews()
        val inflater = LayoutInflater.from(context)
        content.rows.forEachIndexed { index, row ->
            val rowBinding = IncludeBrandDesignPreferenceRowBinding.inflate(inflater, binding.preferenceRows, false)
            if (index > 0) {
                rowBinding.root.updateLayoutParams<MarginLayoutParams> {
                    topMargin = context.resources.getDimensionPixelSize(CommonR.dimen.keyline_4)
                }
            }
            with(rowBinding.preferenceRowItem) {
                setIcon(row.iconRes)
                setPrimaryText(row.primaryText.resolve(context))
                setSecondaryText(row.secondaryText.resolve(context))
                isChecked = state.value.enabled.getValue(row.preference)
                setOnCheckedChangeListener { checked ->
                    state.update { it.copy(enabled = it.enabled + (row.preference to checked)) }
                }
            }
            binding.preferenceRows.addView(rowBinding.root)
        }
    }
}
