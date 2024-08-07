/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.feedback

import android.os.Bundle
import android.text.Annotation
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import androidx.core.content.ContextCompat
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ContentFeedbackSubmitBinding
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackReportType.REPORT_PROBLEM
import com.duckduckgo.subscriptions.impl.feedback.pixels.PrivacyProUnifiedFeedbackPixelSender
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SubscriptionFeedbackSubmitFragment : SubscriptionFeedbackFragment(R.layout.content_feedback_submit) {

    private val binding: ContentFeedbackSubmitBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var pixelSender: PrivacyProUnifiedFeedbackPixelSender

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val listener = activity as Listener
        val reportType = requireArguments().getSerializable(EXTRA_REPORT_TYPE) as SubscriptionFeedbackReportType

        if (reportType == REPORT_PROBLEM) {
            binding.feedbackSubmitHeader.show()
            binding.feedbackSubmitByLine.show()
            binding.feedbackSubmitHeader.setClickableLink(
                "faqs_link",
                SpannableString(getText(R.string.feedbackSubmitVpnHeader)),
            ) {
                listener.onFaqsOpened()
            }
        } else {
            binding.feedbackSubmitHeader.gone()
            binding.feedbackSubmitByLine.gone()
        }

        binding.feedbackSubmitButton.setOnClickListener {
            listener.onUserSubmit(binding.feedbackSubmitDescription.text)
        }
    }

    private fun DaxTextView.setClickableLink(
        annotation: String,
        spannableFullText: SpannableString,
        onClick: () -> Unit,
    ) {
        val annotations = spannableFullText.getSpans(0, spannableFullText.length, Annotation::class.java)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                onClick()
            }
        }

        annotations?.find { it.value == annotation }?.let {
            spannableFullText.apply {
                setSpan(
                    clickableSpan,
                    spannableFullText.getSpanStart(it),
                    spannableFullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    UnderlineSpan(),
                    spannableFullText.getSpanStart(it),
                    spannableFullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(context, com.duckduckgo.mobile.android.R.color.cornflowerBlue),
                    ),
                    spannableFullText.getSpanStart(it),
                    spannableFullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        this.apply {
            text = spannableFullText
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    interface Listener {
        fun onUserSubmit(description: String)
        fun onFaqsOpened()
    }

    companion object {
        private const val EXTRA_REPORT_TYPE = "EXTRA_REPORT_TYPE"
        internal fun instance(
            reportType: SubscriptionFeedbackReportType,
        ): SubscriptionFeedbackSubmitFragment = SubscriptionFeedbackSubmitFragment().apply {
            val args = Bundle()
            args.putSerializable(EXTRA_REPORT_TYPE, reportType)
            arguments = args
        }
    }
}
