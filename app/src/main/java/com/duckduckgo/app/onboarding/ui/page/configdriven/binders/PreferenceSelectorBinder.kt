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
import androidx.core.view.children
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

    /** Rows shown only while their parent preference is on, keyed by that parent. */
    private val dependentRows = mutableMapOf<OnboardingPreference, MutableList<View>>()

    /** What a dependent row's fade is currently heading towards, so an interrupted fade doesn't land on the old target. */
    private val fadeTarget = mutableMapOf<View, Boolean>()

    override fun bind(
        content: ContentConfig.PreferenceSelector,
        state: MutableStateFlow<PreferenceSelectorContentState>,
        scope: BindScope,
    ): ContentHandle {
        val context = binding.root.context

        populateRows(content, state, scope)

        binding.preferenceSelectorTitle.setTitle(content.title.resolve(context))
        binding.caption.text = content.caption?.resolve(context)
        binding.caption.isVisible = content.caption != null

        return ContentHandle(
            title = binding.preferenceSelectorTitle,
            fadeTargets = listOf(binding.preferenceSelectorContentContainer),
            result = {
                NewUserOnboardingEvent.PreferenceSelectorConfirmed(state.value.enabled)
            },
            unbind = {
                // Clearing first leaves a fade that is cancelled below with nothing to land on, so it cannot
                // ask the card to tween once the next screen owns it.
                dependentRows.clear()
                fadeTarget.clear()
                binding.preferenceRows.children.forEach { it.animate().cancel() }
                binding.preferenceRows.removeAllViews()
            },
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
        dependentRows.clear()
        fadeTarget.clear()
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
                setSecondaryText(row.secondaryText?.resolve(context))
                isChecked = state.value.enabled.getValue(row.preference)
                setOnCheckedChangeListener { checked ->
                    state.update { it.copy(enabled = it.enabled + (row.preference to checked)) }
                    showDependentsOf(row.preference, checked, scope)
                }
            }
            row.dependsOn?.let { parent ->
                val shown = state.value.enabled[parent] != false
                dependentRows.getOrPut(parent) { mutableListOf() } += rowBinding.root
                fadeTarget[rowBinding.root] = shown
                rowBinding.root.isVisible = shown
            }
            binding.preferenceRows.addView(rowBinding.root)
        }
    }

    private fun showDependentsOf(
        preference: OnboardingPreference,
        visible: Boolean,
        scope: BindScope,
    ) {
        dependentRows[preference].orEmpty().forEach { row ->
            if (fadeTarget.put(row, visible) == visible) return@forEach
            if (visible) showRow(row, scope) else hideRow(row, scope)
        }
    }

    /**
     * A row entering or leaving resizes the card, so the card is asked to tween into the new bounds rather
     * than jump to them. The row itself fades: taking it out of the layout is what changes the card's size,
     * so that only happens once it has faded out, and on the way back in it appears before it is visible.
     */
    private fun showRow(row: View, scope: BindScope) {
        if (!row.isVisible) {
            row.alpha = 0f
            scope.animateCardBounds(DEPENDENT_ROW_MORPH_MS)
            row.isVisible = true
        }
        row.animate().alpha(1f).setDuration(DEPENDENT_ROW_FADE_MS).start()
    }

    private fun hideRow(row: View, scope: BindScope) {
        row.animate()
            .alpha(0f)
            .setDuration(DEPENDENT_ROW_FADE_MS)
            .withEndAction {
                if (fadeTarget[row] != false) return@withEndAction
                scope.animateCardBounds(DEPENDENT_ROW_MORPH_MS)
                row.isVisible = false
            }
            .start()
    }

    private companion object {
        const val DEPENDENT_ROW_FADE_MS = 200L
        const val DEPENDENT_ROW_MORPH_MS = 400L
    }
}
