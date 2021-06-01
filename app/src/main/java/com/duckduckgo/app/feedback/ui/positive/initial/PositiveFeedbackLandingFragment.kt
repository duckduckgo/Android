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

package com.duckduckgo.app.feedback.ui.positive.initial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.databinding.ContentFeedbackPositiveLandingBinding
import com.duckduckgo.app.feedback.ui.common.FeedbackFragment
import com.duckduckgo.app.playstore.PlayStoreUtils
import javax.inject.Inject

class PositiveFeedbackLandingFragment : FeedbackFragment() {

    interface PositiveFeedbackLandingListener {
        fun userSelectedToRateApp()
        fun userSelectedToGiveFeedback()
        fun userGavePositiveFeedbackNoDetails()
    }

    private var _binding: ContentFeedbackPositiveLandingBinding? = null
    private val binding get() = _binding!!

    private val viewModel by bindViewModel<PositiveFeedbackLandingViewModel>()

    private val listener: PositiveFeedbackLandingListener?
        get() = activity as PositiveFeedbackLandingListener

    @Inject
    lateinit var playStoreUtils: PlayStoreUtils

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = ContentFeedbackPositiveLandingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun configureViewModelObservers() {
        viewModel.command.observe(
            this,
            Observer { command ->
                when (command) {
                    Command.LaunchPlayStore -> {
                        launchPlayStore()
                        listener?.userSelectedToRateApp()
                    }
                    Command.Exit -> {
                        listener?.userGavePositiveFeedbackNoDetails()
                    }
                    Command.LaunchShareFeedbackPage -> {
                        listener?.userSelectedToGiveFeedback()
                    }
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun configureListeners() {
        binding.rateAppButton.setOnClickListener { viewModel.userSelectedToRateApp() }
        binding.shareFeedbackButton.setOnClickListener { viewModel.userSelectedToProvideFeedbackDetails() }
        binding.cancelButton.setOnClickListener { viewModel.userFinishedGivingPositiveFeedback() }
    }

    private fun launchPlayStore() {
        playStoreUtils.launchPlayStore()
    }

    companion object {
        fun instance(): PositiveFeedbackLandingFragment {
            return PositiveFeedbackLandingFragment()
        }
    }
}
