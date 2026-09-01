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

package com.duckduckgo.feedback.impl.ui.initial

import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoTheme.DARK
import com.duckduckgo.common.ui.DuckDuckGoTheme.LIGHT
import com.duckduckgo.common.ui.DuckDuckGoTheme.SYSTEM_DEFAULT
import com.duckduckgo.common.ui.isInNightMode
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.common.ui.store.ThemingDataStore
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.feedback.impl.R
import com.duckduckgo.feedback.impl.databinding.ContentFeedbackBinding
import com.duckduckgo.feedback.impl.ui.common.FeedbackFragment
import com.duckduckgo.feedback.impl.ui.common.resolveFeedbackButtonAsset
import com.duckduckgo.feedback.impl.ui.initial.InitialFeedbackFragmentViewModel.Command.*
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

    @Inject
    lateinit var appBrandDesignUpdateToggles: AppBrandDesignUpdateToggles

    private val binding: ContentFeedbackBinding by viewBinding()

    private val viewModel by bindViewModel<InitialFeedbackFragmentViewModel>()

    private val listener: InitialFeedbackListener?
        get() = activity as InitialFeedbackListener

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (appBrandDesignUpdateToggles.pictograms().isEnabled()) {
            renderBrandUpdateButtons()
        } else {
            when (themingDataStore.theme) {
                SYSTEM_DEFAULT -> if (requireContext().isInNightMode()) renderDarkButtons() else renderLightButtons()
                DARK -> renderDarkButtons()
                LIGHT -> renderLightButtons()
            }
        }
    }

    private fun renderBrandUpdateButtons() {
        binding.positiveFeedbackButton.visibility = GONE
        binding.negativeFeedbackButton.visibility = GONE
        binding.positiveFeedbackBrandButton.visibility = VISIBLE
        binding.negativeFeedbackBrandButton.visibility = VISIBLE
        binding.positiveFeedbackBrandIcon.setImageResource(
            resolveFeedbackButtonAsset(
                isPositive = true,
                isLightMode = true,
                isPictogramsEnabled = true,
            ),
        )
        binding.negativeFeedbackBrandIcon.setImageResource(
            resolveFeedbackButtonAsset(
                isPositive = false,
                isLightMode = true,
                isPictogramsEnabled = true,
            ),
        )
    }

    private fun renderLightButtons() {
        showLegacyButtons()
        binding.positiveFeedbackButton.setImageResource(R.drawable.button_happy_light_theme)
        binding.negativeFeedbackButton.setImageResource(R.drawable.button_sad_light_theme)
    }

    private fun renderDarkButtons() {
        showLegacyButtons()
        binding.positiveFeedbackButton.setImageResource(R.drawable.button_happy_dark_theme)
        binding.negativeFeedbackButton.setImageResource(R.drawable.button_sad_dark_theme)
    }

    private fun showLegacyButtons() {
        binding.positiveFeedbackButton.visibility = VISIBLE
        binding.negativeFeedbackButton.visibility = VISIBLE
        binding.positiveFeedbackBrandButton.visibility = GONE
        binding.negativeFeedbackBrandButton.visibility = GONE
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
        binding.positiveFeedbackBrandButton.setOnClickListener { viewModel.onPositiveFeedback() }
        binding.negativeFeedbackBrandButton.setOnClickListener { viewModel.onNegativeFeedback() }
    }

    companion object {
        fun instance(): InitialFeedbackFragment {
            return InitialFeedbackFragment()
        }
    }
}
