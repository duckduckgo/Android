/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.subscriptions.internal.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.impl.CurrentPurchase
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.billing.SubscriptionReplacementMode
import com.duckduckgo.subscriptions.internal.SubsSettingPlugin
import com.duckduckgo.subscriptions.internal.databinding.SubsSimpleViewBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PoC dev setting to validate whether a deferred PRO → PLUS plan change can be cancelled via
 * the Billing Library by re-subscribing to the current (non-pending) plan with WITHOUT_PRORATION.
 *
 * Requires an active subscription with a pending plan change (hasPendingChange == true).
 * No backend changes are needed — the purchase confirmation flow handles it.
 */
@InjectWith(ViewScope::class)
class CancelPendingPlanChangeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var subscriptionsManager: SubscriptionsManager

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val binding: SubsSimpleViewBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.root.setPrimaryText("Cancel Pending Plan Change (PoC)")
        binding.root.setSecondaryText("Attempt to revert a deferred downgrade by re-subscribing to the current plan with WITHOUT_PRORATION")

        val lifecycleOwner = findViewTreeLifecycleOwner()
        lifecycleOwner?.lifecycleScope?.launch(dispatcherProvider.io()) {
            subscriptionsManager.currentPurchaseState.collect {
                when (it) {
                    is CurrentPurchase.Success -> {
                        launch(dispatcherProvider.main()) {
                            Toast.makeText(context, "Pending plan change cancelled successfully", Toast.LENGTH_LONG).show()
                        }
                    }
                    is CurrentPurchase.Failure -> {
                        launch(dispatcherProvider.main()) {
                            Toast.makeText(context, "Failed to cancel pending change: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    is CurrentPurchase.Canceled -> {
                        launch(dispatcherProvider.main()) {
                            Toast.makeText(context, "Cancellation flow dismissed by user", Toast.LENGTH_LONG).show()
                        }
                    }
                    else -> {}
                }
            }
        }

        checkStatusAndConfigureView()
    }

    private fun checkStatusAndConfigureView() {
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: run {
            configureDisabledState("Unable to check subscription status")
            return
        }

        lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io()) {
            val subscription = subscriptionsManager.getSubscription()
            launch(dispatcherProvider.main()) {
                when {
                    subscription == null -> configureDisabledState("No active subscription found")
                    !subscription.hasPendingChange -> configureDisabledState("No pending plan change to cancel (hasPendingChange = false)")
                    else -> {
                        val pendingTier = subscription.pendingPlans.firstOrNull()?.tier?.value ?: "unknown"
                        configureEnabledState(
                            currentPlanId = subscription.productId,
                            pendingTier = pendingTier,
                        )
                    }
                }
            }
        }
    }

    private fun configureEnabledState(currentPlanId: String, pendingTier: String) {
        binding.root.setSecondaryText(
            "Current: $currentPlanId | Pending change to: $pendingTier — tap to cancel",
        )
        binding.root.setEnabled(true)
        binding.root.setClickListener {
            triggerCancellation(currentPlanId)
        }
    }

    private fun configureDisabledState(reason: String) {
        binding.root.setSecondaryText(reason)
        binding.root.alpha = 0.5f
        binding.root.setClickListener {
            Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerCancellation(currentPlanId: String) {
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        val activity = context as? android.app.Activity ?: run {
            Toast.makeText(context, "Error: Activity context required for billing flow", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io()) {
            val offer = subscriptionsManager.getSubscriptionOffer().find {
                it.planId == currentPlanId && it.offerId == null
            }

            if (offer == null) {
                launch(dispatcherProvider.main()) {
                    Toast.makeText(context, "Could not find offer for plan: $currentPlanId", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            launch(dispatcherProvider.main()) {
                Toast.makeText(context, "Launching billing flow to cancel pending change...", Toast.LENGTH_SHORT).show()
            }

            subscriptionsManager.switchSubscriptionPlan(
                activity = activity,
                planId = offer.planId,
                offerId = null,
                replacementMode = SubscriptionReplacementMode.WITHOUT_PRORATION,
                origin = "dev_settings_cancel_pending",
            )
        }
    }
}

@ContributesMultibinding(ActivityScope::class)
class CancelPendingPlanChangeViewPlugin @Inject constructor() : SubsSettingPlugin {
    override fun getView(context: Context): View = CancelPendingPlanChangeView(context)
}
