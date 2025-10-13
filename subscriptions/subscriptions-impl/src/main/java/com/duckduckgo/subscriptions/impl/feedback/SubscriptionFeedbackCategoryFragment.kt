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
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN_US
import com.duckduckgo.subscriptions.impl.databinding.ContentFeedbackCategoryBinding
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.DUCK_AI
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.ITR
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.PIR
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.SUBS_AND_PAYMENTS
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.VPN
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.impl.repository.toProductList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SubscriptionFeedbackCategoryFragment : SubscriptionFeedbackFragment(R.layout.content_feedback_category) {
    private val binding: ContentFeedbackCategoryBinding by viewBinding()

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var subscriptionFeature: PrivacyProFeature

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val listener = activity as Listener

        binding.categorySubscription.setOnClickListener {
            listener.onUserClickedCategory(SUBS_AND_PAYMENTS)
        }
        binding.categoryVpn.setOnClickListener {
            listener.onUserClickedCategory(VPN)
        }
        binding.categoryItr.setOnClickListener {
            listener.onUserClickedCategory(ITR)
        }
        binding.categoryPir.setOnClickListener {
            listener.onUserClickedCategory(PIR)
        }
        binding.categoryDuckAi.setOnClickListener {
            listener.onUserClickedCategory(DUCK_AI)
        }

        lifecycleScope.launch {
            val duckAiAvailable = isDuckAiAvailable()
            withStarted {
                binding.categoryDuckAi.isVisible = duckAiAvailable
            }
        }

        lifecycleScope.launch {
            val pirAvailable = isPirCategoryAvailable()
            withStarted {
                binding.categoryPir.isVisible = pirAvailable
            }
        }
    }

    private suspend fun isPirCategoryAvailable(): Boolean {
        val subscription = authRepository.getSubscription() ?: return false
        return subscription.productId in listOf(MONTHLY_PLAN_US, YEARLY_PLAN_US)
    }

    private suspend fun isDuckAiAvailable(): Boolean = withContext(dispatcherProvider.io()) {
        val isDuckAiEnabled = subscriptionFeature.duckAiPlus().isEnabled()
        isDuckAiEnabled && authRepository.getEntitlements().toProductList().any { it == Product.DuckAiPlus }
    }

    interface Listener {
        fun onUserClickedCategory(category: SubscriptionFeedbackCategory)
    }

    companion object {
        internal fun instance(): SubscriptionFeedbackCategoryFragment = SubscriptionFeedbackCategoryFragment()
    }
}
