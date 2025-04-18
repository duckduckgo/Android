/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.feedback.ui.initial

import android.app.UiModeManager
import android.os.Bundle
import androidx.core.content.ContextCompat.getSystemService
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentFeedbackBinding
import com.duckduckgo.app.feedback.ui.common.FeedbackFragment
import com.duckduckgo.app.feedback.ui.initial.InitialFeedbackFragmentViewModel.Command.*
import com.duckduckgo.common.ui.DuckDuckGoTheme.DARK
import com.duckduckgo.common.ui.DuckDuckGoTheme.EXPERIMENT_DARK
import com.duckduckgo.common.ui.DuckDuckGoTheme.EXPERIMENT_LIGHT
import com.duckduckgo.common.ui.DuckDuckGoTheme.LIGHT
import com.duckduckgo.common.ui.DuckDuckGoTheme.SYSTEM_DEFAULT
import com.duckduckgo.common.ui.store.ThemingDataStore
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class InitialFeedbackFragment : FeedbackFragment(R.layout.content_feedback) {

    interface InitialFeedbackListener {
        fun userSelectedPositiveFeedback()
        fun userSelectedNegativeFeedback()
        fun userCancelled()
    }

    @Inject
    lateinit var themingDataStore: ThemingDataStore

    private val binding: ContentFeedbackBinding by viewBinding()

    private val viewModel by bindViewModel<InitialFeedbackFragmentViewModel>()

    private val listener: InitialFeedbackListener?
        get() = activity as InitialFeedbackListener

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        when (themingDataStore.theme) {
            SYSTEM_DEFAULT -> {
                val uiManager = getSystemService(requireContext(), UiModeManager::class.java)
                when (uiManager?.nightMode) {
                    UiModeManager.MODE_NIGHT_YES -> renderDarkButtons()
                    else -> renderLightButtons()
                }
            }
            DARK -> renderDarkButtons()
            LIGHT -> renderLightButtons()
            EXPERIMENT_DARK -> renderDarkButtons()
            EXPERIMENT_LIGHT -> renderLightButtons()
        }
    }

    private fun renderLightButtons() {
        binding.positiveFeedbackButton.setImageResource(R.drawable.button_happy_light_theme)
        binding.negativeFeedbackButton.setImageResource(R.drawable.button_sad_light_theme)
    }

    private fun renderDarkButtons() {
        binding.positiveFeedbackButton.setImageResource(R.drawable.button_happy_dark_theme)
        binding.negativeFeedbackButton.setImageResource(R.drawable.button_sad_dark_theme)
    }

    override fun configureViewModelObservers() {
        viewModel.command.observe(this) {
            when (it) {
                PositiveFeedbackSelected -> listener?.userSelectedPositiveFeedback()
                NegativeFeedbackSelected -> listener?.userSelectedNegativeFeedback()
                UserCancelled -> listener?.userCancelled()
            }
        }
    }

    override fun configureListeners() {
        binding.positiveFeedbackButton.setOnClickListener { viewModel.onPositiveFeedback() }
        binding.negativeFeedbackButton.setOnClickListener { viewModel.onNegativeFeedback() }
    }

    companion object {
        fun instance(): InitialFeedbackFragment {
            return InitialFeedbackFragment()
        }
    }
}
