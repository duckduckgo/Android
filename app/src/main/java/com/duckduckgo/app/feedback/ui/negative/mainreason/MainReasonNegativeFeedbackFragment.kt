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

package com.duckduckgo.app.feedback.ui.negative.mainreason

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.feedback.ui.common.FeedbackFragment
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason
import kotlinx.android.synthetic.main.content_feedback_negative_disambiguation_main_reason.*


class MainReasonNegativeFeedbackFragment : FeedbackFragment() {

    override val fragmentTag: String = "Disambiguation negative feedback"

    interface MainReasonNegativeFeedbackListener {
        fun userSelectedNegativeFeedbackMainReason(type: MainReason)
    }

    private val viewModel by bindViewModel<MainReasonNegativeFeedbackViewModel>()

    private val listener: MainReasonNegativeFeedbackListener?
        get() = activity as MainReasonNegativeFeedbackListener

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.content_feedback_negative_disambiguation_main_reason, container, false)
    }

    override fun configureViewModelObservers() {
        viewModel.command.observe(this, Observer { command ->
            when (command) {
                is MainReasonNegativeFeedbackViewModel.Command.UserSelectedFeedbackType -> {
                    listener?.userSelectedNegativeFeedbackMainReason(command.type)
                }
            }
        })
    }

    override fun configureListeners() {
        optionMissingFeatures.setOnClickListener { viewModel.userSelectedFeedbackType(MainReason.MISSING_BROWSING_FEATURES) }
        optionWebsitesNotLoading.setOnClickListener { viewModel.userSelectedFeedbackType(MainReason.WEBSITES_NOT_LOADING) }
        optionSearchNotGoodEnough.setOnClickListener { viewModel.userSelectedFeedbackType(MainReason.SEARCH_NOT_GOOD_ENOUGH) }
        optionNotEnoughCustomizationOptions.setOnClickListener { viewModel.userSelectedFeedbackType(MainReason.NOT_ENOUGH_CUSTOMIZATIONS) }
        optionAppIsSlowOrBuggy.setOnClickListener { viewModel.userSelectedFeedbackType(MainReason.APP_IS_SLOW_OR_BUGGY) }
        optionNoneOfThese.setOnClickListener { viewModel.userSelectedFeedbackType(MainReason.OTHER) }
    }

    companion object {

        fun instance(): MainReasonNegativeFeedbackFragment {
            val fragment = MainReasonNegativeFeedbackFragment()
            return fragment
        }
    }
}