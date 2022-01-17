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
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentFeedbackOpenEndedFeedbackBinding
import com.duckduckgo.app.feedback.ui.common.FeedbackFragment
import com.duckduckgo.app.feedback.ui.common.LayoutScrollingTouchListener
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.SubReason
import com.duckduckgo.app.feedback.ui.negative.FeedbackTypeDisplay.Companion.mainReasons
import com.duckduckgo.app.feedback.ui.negative.FeedbackTypeDisplay.Companion.subReasons
import com.duckduckgo.app.feedback.ui.negative.openended.ShareOpenEndedNegativeFeedbackViewModel.Command
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class ShareOpenEndedFeedbackFragment : FeedbackFragment(R.layout.content_feedback_open_ended_feedback) {

    interface OpenEndedFeedbackListener {
        fun userProvidedNegativeOpenEndedFeedback(
            mainReason: MainReason,
            subReason: SubReason?,
            feedback: String
        )

        fun userProvidedPositiveOpenEndedFeedback(feedback: String)
        fun userCancelled()
    }

    private val binding: ContentFeedbackOpenEndedFeedbackBinding by viewBinding()

    private val viewModel by bindViewModel<ShareOpenEndedNegativeFeedbackViewModel>()

    private val listener: OpenEndedFeedbackListener?
        get() = activity as OpenEndedFeedbackListener

    private var isPositiveFeedback: Boolean = true

    private var mainReason: MainReason? = null
    private var subReason: SubReason? = null

    override fun configureViewModelObservers() {
        viewModel.command.observe(
            this,
            Observer { command ->
                when (command) {
                    is Command.Exit -> {
                        listener?.userCancelled()
                    }
                    is Command.ExitAndSubmitNegativeFeedback -> {
                        listener?.userProvidedNegativeOpenEndedFeedback(command.mainReason, command.subReason, command.feedback)
                    }
                    is Command.ExitAndSubmitPositiveFeedback -> {
                        listener?.userProvidedPositiveOpenEndedFeedback(command.feedback)
                    }
                }
            }
        )
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (arguments == null) throw IllegalArgumentException("Missing required arguments")

        arguments?.let { args ->
            isPositiveFeedback = args.getBoolean(IS_POSITIVE_FEEDBACK_EXTRA)

            if (isPositiveFeedback) {
                updateDisplayForPositiveFeedback()
            } else {
                updateDisplayForNegativeFeedback(args)
            }
        }
    }

    private fun updateDisplayForPositiveFeedback() {
        binding.title.text = getString(R.string.feedbackShareDetails)
        binding.subtitle.text = getString(R.string.sharePositiveFeedbackWithTheTeam)
        binding.openEndedFeedbackContainer.hint = getString(R.string.whatHaveYouBeenEnjoying)
        binding.emoticonImage.setImageResource(R.drawable.ic_happy_face)
    }

    private fun updateDisplayForNegativeFeedback(args: Bundle) {
        mainReason = args.getSerializable(MAIN_REASON_EXTRA) as MainReason
        subReason = args.getSerializable(SUB_REASON_EXTRA) as SubReason?

        binding.title.text = getDisplayText(mainReason!!)
        binding.subtitle.text = getDisplayText(subReason)
        binding.openEndedFeedbackContainer.hint = getInputHintText(mainReason!!)
        binding.emoticonImage.setImageResource(R.drawable.ic_sad_face)
    }

    private fun getDisplayText(reason: MainReason): String {
        val display = mainReasons[reason] ?: return ""
        return getString(display.titleDisplayResId)
    }

    private fun getDisplayText(reason: SubReason?): String {
        val display = subReasons[reason] ?: return getString(R.string.tellUsHowToImprove)
        return getString(display.subtitleDisplayResId)
    }

    private fun getInputHintText(reason: MainReason): String {
        return if (reason == MainReason.OTHER) {
            getString(R.string.feedbackSpecificAsPossible)
        } else {
            getString(R.string.openEndedInputHint)
        }
    }

    override fun configureListeners() {
        with(binding) {
            rootScrollView.doOnNextLayout {
                binding.openEndedFeedback.setOnTouchListener(LayoutScrollingTouchListener(rootScrollView, openEndedFeedbackContainer.y.toInt()))
            }

            submitFeedbackButton.setOnClickListener {
                val openEndedComment = openEndedFeedback.text.toString()
                if (isPositiveFeedback) {
                    viewModel.userSubmittingPositiveFeedback(openEndedComment)
                } else {
                    viewModel.userSubmittingNegativeFeedback(mainReason!!, subReason, openEndedComment)
                }
            }
        }
    }

    companion object {

        private const val MAIN_REASON_EXTRA = "MAIN_REASON_EXTRA"
        private const val SUB_REASON_EXTRA = "SUB_REASON_EXTRA"
        private const val IS_POSITIVE_FEEDBACK_EXTRA = "IS_POSITIVE_FEEDBACK_EXTRA"

        fun instanceNegativeFeedback(
            mainReason: MainReason,
            subReason: SubReason?
        ): ShareOpenEndedFeedbackFragment {
            val fragment = ShareOpenEndedFeedbackFragment()
            fragment.arguments = Bundle().also {
                it.putBoolean(IS_POSITIVE_FEEDBACK_EXTRA, false)
                it.putSerializable(MAIN_REASON_EXTRA, mainReason)

                if (subReason != null) {
                    it.putSerializable(SUB_REASON_EXTRA, subReason)
                }
            }
            return fragment
        }

        fun instancePositiveFeedback(): ShareOpenEndedFeedbackFragment {
            val fragment = ShareOpenEndedFeedbackFragment()
            fragment.arguments = Bundle().also {
                it.putBoolean(IS_POSITIVE_FEEDBACK_EXTRA, true)
            }
            return fragment
        }
    }
}
