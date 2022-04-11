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

package com.duckduckgo.app.feedback.ui.negative.brokensite

import androidx.core.view.doOnNextLayout
import androidx.lifecycle.Observer
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentFeedbackNegativeBrokenSiteFeedbackBinding
import com.duckduckgo.app.feedback.ui.common.FeedbackFragment
import com.duckduckgo.app.feedback.ui.common.LayoutScrollingTouchListener
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

@InjectWith(FragmentScope::class)
class BrokenSiteNegativeFeedbackFragment : FeedbackFragment(R.layout.content_feedback_negative_broken_site_feedback) {

    interface BrokenSiteFeedbackListener {
        fun onProvidedBrokenSiteFeedback(
            feedback: String,
            url: String?
        )

        fun userCancelled()
    }

    private val binding: ContentFeedbackNegativeBrokenSiteFeedbackBinding by viewBinding()

    private val viewModel by bindViewModel<BrokenSiteNegativeFeedbackViewModel>()

    private val listener: BrokenSiteFeedbackListener?
        get() = activity as BrokenSiteFeedbackListener

    override fun configureViewModelObservers() {
        viewModel.command.observe(
            this,
            Observer { command ->
                when (command) {
                    is BrokenSiteNegativeFeedbackViewModel.Command.Exit -> {
                        listener?.userCancelled()
                    }
                    is BrokenSiteNegativeFeedbackViewModel.Command.ExitAndSubmitFeedback -> {
                        listener?.onProvidedBrokenSiteFeedback(command.feedback, command.brokenSite)
                    }
                }
            }
        )
    }

    override fun configureListeners() {
        with(binding) {
            submitFeedbackButton.doOnNextLayout {
                brokenSiteInput.setOnTouchListener(LayoutScrollingTouchListener(rootScrollView, brokenSiteInputContainer.y.toInt()))
                openEndedFeedback.setOnTouchListener(LayoutScrollingTouchListener(rootScrollView, openEndedFeedbackContainer.y.toInt()))
            }

            submitFeedbackButton.setOnClickListener {
                val feedback = openEndedFeedback.text.toString()
                val brokenSite = brokenSiteInput.text.toString()

                viewModel.userSubmittingFeedback(feedback, brokenSite)
            }
        }
    }

    companion object {
        fun instance(): BrokenSiteNegativeFeedbackFragment {
            return BrokenSiteNegativeFeedbackFragment()
        }
    }
}
