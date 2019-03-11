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

package com.duckduckgo.app.feedback.ui.negative.openended

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.feedback.ui.common.FeedbackFragment
import com.duckduckgo.app.feedback.ui.negative.FeedbackType
import com.duckduckgo.app.feedback.ui.positive.openended.ShareOpenEndedNegativeFeedbackViewModel
import com.duckduckgo.app.feedback.ui.positive.openended.ShareOpenEndedNegativeFeedbackViewModel.Command
import kotlinx.android.synthetic.main.content_feedback_positive_open_ended_feedback.*


class ShareOpenEndedNegativeFeedbackFragment : FeedbackFragment() {

    override val fragmentTag: String = "Open ended negative feedback"

    interface OpenEndedNegativeFeedbackListener {
        fun onProvidedNegativeOpenEndedFeedback(feedback: String)
        fun userCancelled()
    }

    private val viewModel by bindViewModel<ShareOpenEndedNegativeFeedbackViewModel>()

    private val listener: OpenEndedNegativeFeedbackListener?
        get() = activity as OpenEndedNegativeFeedbackListener

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.content_feedback_negative_open_ended_feedback, container, false)
    }

    override fun configureViewModelObservers() {
        viewModel.command.observe(this, Observer { command ->
            when (command) {
                is Command.Exit -> {
                    listener?.userCancelled()
                }
                is Command.ExitAndSubmit -> {
                    listener?.onProvidedNegativeOpenEndedFeedback(command.feedback)
                }
            }
        })
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        arguments?.let { args ->
            title.text = "to do!"
            subtitle.text = "to do!"
        }
    }

    override fun configureListeners() {
        submitFeedbackButton.setOnClickListener { viewModel.userSubmittingFeedback(openEndedFeedback.text.toString()) }
    }

    companion object {

        private const val MAIN_REASON_EXTRA = "MAIN_REASON_EXTRA"

        fun instance(mainReason: FeedbackType.MainReason): ShareOpenEndedNegativeFeedbackFragment {
            val fragment = ShareOpenEndedNegativeFeedbackFragment()
            fragment.arguments = Bundle().also {
                it.putInt(MAIN_REASON_EXTRA, mainReason.ordinal)
            }
            return fragment
        }
    }
}