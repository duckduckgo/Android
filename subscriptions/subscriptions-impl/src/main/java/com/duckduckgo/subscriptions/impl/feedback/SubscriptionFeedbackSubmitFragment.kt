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
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.showKeyboard
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ContentFeedbackSubmitBinding
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackReportType.GENERAL_FEEDBACK
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackReportType.REPORT_PROBLEM
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SubscriptionFeedbackSubmitFragment : SubscriptionFeedbackFragment(R.layout.content_feedback_submit) {
    private val binding: ContentFeedbackSubmitBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var privacyProFeature: PrivacyProFeature

    private val submitTextWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence,
            start: Int,
            count: Int,
            after: Int,
        ) {
        }

        override fun onTextChanged(
            s: CharSequence,
            start: Int,
            before: Int,
            count: Int,
        ) {
            // get the content of both the edit text
            val description = binding.feedbackSubmitDescription.text.trim()

            // check whether both the fields are empty or not
            binding.feedbackSubmitButton.isEnabled = description.isNotEmpty()
        }

        override fun afterTextChanged(s: Editable) {
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val listener = activity as Listener
        val reportType = requireArguments().getSerializable(EXTRA_REPORT_TYPE) as SubscriptionFeedbackReportType
        binding.feedbackSubmitDescription.addTextChangedListener(submitTextWatcher)

        if (reportType == REPORT_PROBLEM) {
            binding.feedbackSubmitHeader.show()
            binding.feedbackSubmitByLine.show()
            binding.feedbackSubmitHeader.setClickableLink(
                "faqs_link",
                SpannableString(getText(R.string.feedbackSubmitVpnHeader)),
            ) {
                listener.onFaqsOpened()
            }
            binding.feedbackSubmitDescription.hint = getString(R.string.feedbackSubmitVpnDescriptionHint)

            if (privacyProFeature.useSubscriptionSupport().isEnabled()) {
                binding.hideEmail()
                binding.feedbackSubmitContactSupportByLine.show()
                binding.feedbackSubmitContactSupportByLine.setClickableLink(
                    "support_link",
                    SpannableString(getText(R.string.feedbackSubmitContactSupport)),
                ) {
                    listener.onContactSupportOpened()
                }
            } else {
                binding.showEmail()
                binding.feedbackSubmitContactSupportByLine.gone()
            }
        } else {
            binding.feedbackSubmitHeader.gone()
            binding.feedbackSubmitByLine.gone()
            binding.feedbackSubmitContactSupportByLine.gone()
            binding.hideEmail()
            if (reportType == GENERAL_FEEDBACK) {
                binding.feedbackSubmitDescription.hint = getString(R.string.feedbackSubmitGeneralDescriptionHint)
            } else {
                binding.feedbackSubmitDescription.hint = getString(R.string.feedbackSubmitFeatureRequestDescriptionHint)
            }
        }

        binding.feedbackSubmitButton.setOnClickListener {
            listener.onUserSubmit(binding.feedbackSubmitDescription.text.trim(), binding.feedbackSubmitEmail.text.trim())
        }

        binding.feedbackSubmitDescription.showKeyboard()
    }

    private fun ContentFeedbackSubmitBinding.showEmail() {
        feedbackSubmitEmailByLine.show()
        feedbackSubmitEmail.show()
    }

    private fun ContentFeedbackSubmitBinding.hideEmail() {
        feedbackSubmitEmailByLine.gone()
        feedbackSubmitEmail.gone()
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
                        context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorAccentBlue),
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
        fun onUserSubmit(
            description: String,
            email: String? = null,
        )

        fun onFaqsOpened()
        fun onContactSupportOpened()
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
