/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.SubscriptionsActivity.Companion.SubscriptionsScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionsBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionsScreenWithEmptyParams::class)
class SubscriptionsActivity : DuckDuckGoActivity() {

    private val viewModel: SubscriptionsViewModel by bindViewModel()
    private val binding: ActivitySubscriptionsBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.subscriptionsFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach {
            if (it.hasSubscription == true) {
                renderPurchases(it.purchases)
            }
            if (it.subscriptionDetails != null) {
                renderProductDetails(it.subscriptionDetails)
            }
        }.launchIn(lifecycleScope)

        setContentView(binding.root)
        setupToolbar(toolbar)
    }

    private fun renderPurchases(purchases: List<Purchase>?) {
        if (!purchases.isNullOrEmpty()) {
            if (purchases.first().products.first() != null) {
                val purchase = purchases.first()
                binding.purchaseDetails.text = "You are subscribed to ${purchase.products.first()}"
                binding.buyButton.isEnabled = false
            }
        } else {
            binding.purchaseDetails.text = "You are not subscribed yet"
            binding.buyButton.isEnabled = true
        }
    }

    private fun renderProductDetails(productDetails: ProductDetails) {
        binding.buyButton.setOnClickListener {
            viewModel.buySubscription(this, productDetails)
        }
        binding.description.text = productDetails.description
        binding.name.text = productDetails.name
    }

    companion object {
        object SubscriptionsScreenWithEmptyParams : GlobalActivityStarter.ActivityParams
    }
}
