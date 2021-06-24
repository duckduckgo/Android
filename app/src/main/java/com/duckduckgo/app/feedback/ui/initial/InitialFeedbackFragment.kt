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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentFeedbackBinding
import com.duckduckgo.app.feedback.ui.common.FeedbackFragment
import com.duckduckgo.app.feedback.ui.initial.InitialFeedbackFragmentViewModel.Command.*
import com.duckduckgo.app.global.DuckDuckGoTheme
import com.duckduckgo.app.settings.db.SettingsDataStore
import javax.inject.Inject

class InitialFeedbackFragment : FeedbackFragment() {

    interface InitialFeedbackListener {
        fun userSelectedPositiveFeedback()
        fun userSelectedNegativeFeedback()
        fun userCancelled()
    }

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private var _binding: ContentFeedbackBinding? = null
    private val binding get() = _binding!!

    private val viewModel by bindViewModel<InitialFeedbackFragmentViewModel>()

    private val listener: InitialFeedbackListener?
        get() = activity as InitialFeedbackListener

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = ContentFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (settingsDataStore.theme == DuckDuckGoTheme.LIGHT) {
            binding.positiveFeedbackButton.setImageResource(R.drawable.button_happy_light_theme)
            binding.negativeFeedbackButton.setImageResource(R.drawable.button_sad_light_theme)
        } else {
            binding.positiveFeedbackButton.setImageResource(R.drawable.button_happy_dark_theme)
            binding.negativeFeedbackButton.setImageResource(R.drawable.button_sad_dark_theme)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun configureViewModelObservers() {
        viewModel.command.observe(
            this,
            Observer {
                when (it) {
                    PositiveFeedbackSelected -> listener?.userSelectedPositiveFeedback()
                    NegativeFeedbackSelected -> listener?.userSelectedNegativeFeedback()
                    UserCancelled -> listener?.userCancelled()
                }
            }
        )
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
