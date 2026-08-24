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
import android.widget.RadioButton
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignSingleChoiceBinding
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignSingleChoiceRowBinding
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.SingleChoiceContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import com.duckduckgo.common.utils.extensions.preventWidows
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class SingleChoiceBinder(
    private val binding: IncludeBrandDesignSingleChoiceBinding,
) : StatefulDialogBinder<ContentConfig.SingleChoice, SingleChoiceContentState> {

    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.SingleChoice,
        state: MutableStateFlow<SingleChoiceContentState>,
        scope: BindScope,
    ): ContentHandle {
        val context = binding.root.context

        binding.singleChoiceTitle.setTitle(content.title.resolve(context))
        binding.singleChoiceBody.text = content.body.resolve(context).preventWidows()

        val rows = populateRows(content, state)

        // Every row renders off the one selected option, so a pick deselects the others without them knowing about each other.
        state.onEach { current ->
            rows.forEach { (option, rowBinding) ->
                val selected = option == current.selected
                rowBinding.root.isSelected = selected
                rowBinding.singleChoiceRowRadioButton.isChecked = selected
            }
        }.launchIn(scope.coroutineScope)

        return ContentHandle(
            title = binding.singleChoiceTitle,
            fadeTargets = listOf(binding.singleChoiceContentContainer),
            result = {
                NewUserOnboardingEvent.SingleChoiceConfirmed(state.value.selected)
            },
            unbind = {
                rows.forEach { (_, rowBinding) -> rowBinding.root.setOnClickListener(null) }
                binding.radioButtonRows.removeAllViews()
            },
        )
    }

    private fun populateRows(
        content: ContentConfig.SingleChoice,
        state: MutableStateFlow<SingleChoiceContentState>,
    ): List<Pair<Option, IncludeBrandDesignSingleChoiceRowBinding>> {
        binding.radioButtonRows.removeAllViews()
        val inflater = LayoutInflater.from(binding.root.context)
        return content.rows.map { row ->
            val rowBinding = IncludeBrandDesignSingleChoiceRowBinding.inflate(inflater, binding.radioButtonRows, false)
            row.iconRes?.let { rowBinding.singleChoiceRowIcon.setImageResource(it) }
            rowBinding.singleChoiceRowIcon.isVisible = row.iconRes != null
            rowBinding.singleChoiceRowPrimaryText.text = row.label
            rowBinding.root.setOnClickListener {
                state.update { it.copy(selected = row) }
            }
            // The row, not the radio button, carries the click, so it also has to carry the radio semantics —
            // otherwise the label is announced with no indication of what is currently picked.
            ViewCompat.setAccessibilityDelegate(
                rowBinding.root,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = RadioButton::class.java.name
                        info.isCheckable = true
                        info.isChecked = host.isSelected
                    }
                },
            )
            binding.radioButtonRows.addView(rowBinding.root)
            row to rowBinding
        }
    }
}
