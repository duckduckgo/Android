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
import android.view.View
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.subscriptions.api.SubscriptionRebrandingFeatureToggle
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ContentFeedbackGeneralBinding
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SubscriptionFeedbackGeneralFragment : SubscriptionFeedbackFragment(R.layout.content_feedback_general) {

    @Inject
    lateinit var subscriptionRebrandingFeatureToggle: SubscriptionRebrandingFeatureToggle

    private val binding: ContentFeedbackGeneralBinding by viewBinding()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val listener = activity as Listener

        binding.browserFeedback.setOnClickListener {
            listener.onBrowserFeedbackClicked()
        }

        if (subscriptionRebrandingFeatureToggle.isSubscriptionRebrandingEnabled()) {
            binding.pproFeedback.setPrimaryText(getString(R.string.feedbackGeneralSubscription))
        } else {
            binding.pproFeedback.setPrimaryText(getString(R.string.feedbackGeneralPpro))
        }
        binding.pproFeedback.setOnClickListener {
            listener.onPproFeedbackClicked()
        }
    }

    interface Listener {
        fun onBrowserFeedbackClicked()
        fun onPproFeedbackClicked()
    }

    companion object {
        internal fun instance(): SubscriptionFeedbackGeneralFragment = SubscriptionFeedbackGeneralFragment()
    }
}
