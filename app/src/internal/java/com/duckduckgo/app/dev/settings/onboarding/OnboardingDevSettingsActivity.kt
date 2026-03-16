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

package com.duckduckgo.app.dev.settings.onboarding

import android.os.Bundle
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.ActivityOnboardingDevSettingsBinding
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.edgetoedge.api.EdgeToEdge
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(OnboardingDevSettingsScreen::class)
class OnboardingDevSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdge: EdgeToEdge

    private val binding: ActivityOnboardingDevSettingsBinding by viewBinding()
    private val viewModel: OnboardingDevSettingsViewModel by bindViewModel()

    private val completedToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onOnboardingCompletedToggled(isChecked)
    }

    private val skippedToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onOnboardingSkippedToggled(isChecked)
    }

    private val ctaRowListeners = mutableMapOf<CtaId, OnCheckedChangeListener>()
    private val ctaRowsById = mutableMapOf<CtaId, OneLineListItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge.enableIfToggled(this)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    private fun buildCtaList(visibleCtaIds: List<CtaId>) {
        val container = binding.ctaListContainer
        container.removeAllViews()
        ctaRowsById.clear()
        ctaRowListeners.clear()
        visibleCtaIds.forEach { ctaId ->
            val row = createCtaRow(ctaId)
            ctaRowsById[ctaId] = row
            container.addView(row)
        }
    }

    private fun createCtaRow(ctaId: CtaId): OneLineListItem {
        val primaryText = if (viewModel.isIndependentCta(ctaId)) "${ctaId.name} (manual only)" else ctaId.name
        val row = OneLineListItem(this).apply {
            setPrimaryText(primaryText)
            showSwitch()
        }
        val listener = OnCheckedChangeListener { _, isChecked ->
            viewModel.onCtaDismissedToggled(ctaId, isChecked)
        }
        ctaRowListeners[ctaId] = listener
        row.setOnCheckedChangeListener(listener)
        return row
    }

    private fun configureUiEventHandlers() {
        binding.onboardingCompletedToggle.setOnCheckedChangeListener(completedToggleListener)
        binding.onboardingSkippedToggle.setOnCheckedChangeListener(skippedToggleListener)
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                if (state.visibleCtaIds.isNotEmpty() && ctaRowsById.isEmpty()) {
                    buildCtaList(state.visibleCtaIds)
                }
                binding.onboardingCompletedToggle.quietlySetIsChecked(
                    state.onboardingCompleted,
                    completedToggleListener,
                )
                binding.onboardingSkippedToggle.quietlySetIsChecked(
                    state.onboardingSkipped,
                    skippedToggleListener,
                )
                // Skip is allowed when not completed (user can skip), or when completed via skip (user can turn skip off).
                // Skip is disabled when completed via dialogs (cannot skip once already completed).
                binding.onboardingSkippedToggle.isEnabled = !state.onboardingCompleted || state.onboardingSkipped

                state.visibleCtaIds.forEach { ctaId ->
                    val isDismissed = state.ctaDismissedStates[ctaId] ?: false
                    ctaRowsById[ctaId]?.quietlySetIsChecked(isDismissed, ctaRowListeners[ctaId])
                }
            }
            .launchIn(lifecycleScope)
    }
}

data object OnboardingDevSettingsScreen : GlobalActivityStarter.ActivityParams {
    private fun readResolve(): Any = OnboardingDevSettingsScreen
}
