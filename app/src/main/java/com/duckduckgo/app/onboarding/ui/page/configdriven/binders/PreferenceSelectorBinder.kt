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
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignPreferenceRowBinding
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignPreferenceSelectorBinding
import com.duckduckgo.app.onboarding.OnboardingPreference
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

        populateRows(content, state, scope)

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
        scope: BindScope,
    ) {
        val context = binding.root.context
        binding.preferenceRows.removeAllViews()
        val inflater = LayoutInflater.from(context)
        val rowViews = mutableMapOf<OnboardingPreference, View>()
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
                    showDependentsOf(content, rowViews, row.preference, checked, scope)
                }
            }
            rowViews[row.preference] = rowBinding.root
            binding.preferenceRows.addView(rowBinding.root)
        }
        content.rows.forEach { row ->
            val parent = row.dependsOn ?: return@forEach
            rowViews.getValue(row.preference).isVisible = state.value.enabled[parent] != false
        }
    }

    /**
     * A dependent row appearing or leaving resizes the card, so the card is asked to tween into the new
     * bounds rather than jump to them in the frame the row's visibility changes.
     */
    private fun showDependentsOf(
        content: ContentConfig.PreferenceSelector,
        rowViews: Map<OnboardingPreference, View>,
        preference: OnboardingPreference,
        visible: Boolean,
        scope: BindScope,
    ) {
        val dependents = content.rows.filter { it.dependsOn == preference }
        if (dependents.isEmpty()) return
        scope.animateCardBounds(DEPENDENT_ROW_DURATION_MS)
        dependents.forEach { rowViews[it.preference]?.isVisible = visible }
    }

    private companion object {
        const val DEPENDENT_ROW_DURATION_MS = 400L
    }
}
