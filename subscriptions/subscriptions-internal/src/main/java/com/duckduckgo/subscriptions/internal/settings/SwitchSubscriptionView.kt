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
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.billing.SubscriptionReplacementMode
import com.duckduckgo.subscriptions.internal.SubsSettingPlugin
import com.duckduckgo.subscriptions.internal.databinding.SubsSimpleViewBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ViewScope::class)
class SwitchSubscriptionView @JvmOverloads constructor(
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

        binding.root.setPrimaryText("Switch subscription")
        binding.root.setSecondaryText("If you have an active subscription, you can switch plans here")

        checkSubscriptionStatusAndConfigureView()
    }

    private fun checkSubscriptionStatusAndConfigureView() {
        val lifecycleOwner = findViewTreeLifecycleOwner()
        if (lifecycleOwner == null) {
            configureDisabledState("Unable to check subscription status")
            return
        }

        lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io()) {
            try {
                val hasActiveSubscription = subscriptionsManager.getSubscription() != null

                launch(dispatcherProvider.main()) {
                    if (hasActiveSubscription) {
                        configureEnabledState()
                    } else {
                        configureDisabledState("You need an active subscription to enable this option")
                    }
                }
            } catch (e: Exception) {
                launch(dispatcherProvider.main()) {
                    configureDisabledState("Error checking subscription status: ${e.message}")
                }
            }
        }
    }

    private fun configureEnabledState() {
        binding.root.setEnabled(true)
        binding.root.setClickListener {
            showPlanSelectionDialog()
        }
    }

    private fun configureDisabledState(message: String) {
        // I don't use setEnabled(false) as it prevents click listeners from working
        binding.root.alpha = 0.5f
        binding.root.setClickListener {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlanSelectionDialog() {
        val lifecycleOwner = findViewTreeLifecycleOwner()
        if (lifecycleOwner == null) {
            Toast.makeText(context, "Unable to show dialog", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io()) {
            try {
                val availablePlans = getAvailablePlans()

                if (availablePlans.isEmpty()) {
                    launch(dispatcherProvider.main()) {
                        Toast.makeText(context, "No subscription plans available for switching", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                launch(dispatcherProvider.main()) {
                    val options = availablePlans.map { it.displayName }
                    val currentPlanIndex = availablePlans.indexOfFirst { it.isCurrentPlan }

                    RadioListAlertDialogBuilder(context)
                        .setTitle("Switch Subscription Plan")
                        .setOptions(options, currentPlanIndex)
                        .setPositiveButton(android.R.string.ok)
                        .setNegativeButton(android.R.string.cancel)
                        .addEventListener(object : RadioListAlertDialogBuilder.EventListener() {
                            override fun onPositiveButtonClicked(selectedItem: Int) {
                                val selectedPlan = availablePlans[selectedItem - 1]

                                if (selectedPlan.isCurrentPlan) {
                                    Toast.makeText(context, "You are already subscribed to this plan", Toast.LENGTH_SHORT).show()
                                    return
                                }

                                showReplacementModeDialog(selectedPlan, lifecycleOwner)
                            }
                        })
                        .show()
                }
            } catch (e: Exception) {
                launch(dispatcherProvider.main()) {
                    Toast.makeText(context, "Error loading subscription offers: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getAvailablePlans(): List<PlanOption> {
        val offers = subscriptionsManager.getSubscriptionOffer()
        val currentSubscription = subscriptionsManager.getSubscription()

        val currentPlanId = currentSubscription?.productId
        val deduplicatedOffers = offers
            .groupBy { it.planId }
            .mapValues { (_, offersForPlan) ->
                // Get plans without offerId
                offersForPlan.minByOrNull { if (it.offerId == null) 0 else 1 }!!
            }
            .values

        val planOptions = mutableListOf<PlanOption>()
        deduplicatedOffers.forEach { offer ->
            val isCurrentPlan = offer.planId == currentPlanId
            val displayText = when {
                isCurrentPlan -> {
                    when (offer.planId) {
                        MONTHLY_PLAN_US, MONTHLY_PLAN_ROW -> "Monthly (current)"
                        YEARLY_PLAN_US, YEARLY_PLAN_ROW -> "Yearly (current)"
                        else -> "${offer.planId} (current)"
                    }
                }
                else -> {
                    val price = offer.pricingPhases.firstOrNull()?.formattedPrice ?: "N/A"
                    when (offer.planId) {
                        MONTHLY_PLAN_US, MONTHLY_PLAN_ROW -> "Monthly ($price)"
                        YEARLY_PLAN_US, YEARLY_PLAN_ROW -> "Yearly ($price)"
                        else -> "${offer.planId} ($price)"
                    }
                }
            }

            planOptions.add(PlanOption(offer.planId, displayText, offer.offerId, isCurrentPlan))
        }

        return planOptions
    }

    private fun showReplacementModeDialog(
        planOption: PlanOption,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    ) {
        val replacementModes = arrayOf(
            ReplacementModeOption(
                mode = SubscriptionReplacementMode.CHARGE_PRORATED_PRICE,
                displayName = "Charge Prorated Price",
                description = "New plan starts immediately. You'll be charged a prorated amount for the remaining period.",
            ),
            ReplacementModeOption(
                mode = SubscriptionReplacementMode.DEFERRED,
                displayName = "Deferred",
                description = "New plan starts when current subscription expires. No immediate charge.",
            ),
            ReplacementModeOption(
                mode = SubscriptionReplacementMode.WITHOUT_PRORATION,
                displayName = "Without Proration",
                description = "New plan starts immediately. New price charged on next billing cycle.",
            ),
            ReplacementModeOption(
                mode = SubscriptionReplacementMode.CHARGE_FULL_PRICE,
                displayName = "Charge Full Price",
                description = "New plan starts immediately. You'll be charged the full price plus remaining time from old plan.",
            ),
        )

        val defaultIndex = replacementModes.indexOfFirst { it.mode == SubscriptionReplacementMode.WITHOUT_PRORATION }
        val options = replacementModes.map { it.displayName }

        RadioListAlertDialogBuilder(context)
            .setTitle("Choose Replacement Mode")
            .setOptions(options, defaultIndex + 1)
            .setPositiveButton(android.R.string.ok)
            .setNegativeButton(android.R.string.cancel)
            .addEventListener(object : RadioListAlertDialogBuilder.EventListener() {
                override fun onPositiveButtonClicked(selectedItem: Int) {
                    val selectedReplacementMode = replacementModes[selectedItem - 1].mode
                    switchToPlan(planOption, lifecycleOwner, selectedReplacementMode)
                }
            })
            .show()
    }

    private fun switchToPlan(
        planOption: PlanOption,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        replacementMode: SubscriptionReplacementMode,
        onComplete: () -> Unit = {},
    ) {
        lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io()) {
            try {
                launch(dispatcherProvider.main()) {
                    Toast.makeText(context, "Switching to ${planOption.displayName}...", Toast.LENGTH_SHORT).show()
                }

                val activity = context as? android.app.Activity
                if (activity == null) {
                    launch(dispatcherProvider.main()) {
                        Toast.makeText(context, "Error: Activity context required for billing flow", Toast.LENGTH_SHORT).show()
                        onComplete()
                    }
                    return@launch
                }

                // Call the subscription switch logic
                val success =
                    subscriptionsManager.switchSubscriptionPlan(
                        activity = activity,
                        planId = planOption.planId,
                        offerId = planOption.offerId,
                        replacementMode = replacementMode,
                    )

                launch(dispatcherProvider.main()) {
                    if (success) {
                        Toast.makeText(context, "Successfully switched to ${planOption.displayName}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to switch subscription plan", Toast.LENGTH_LONG).show()
                    }
                    onComplete()
                }
            } catch (e: Exception) {
                launch(dispatcherProvider.main()) {
                    Toast.makeText(context, "Error switching plan: ${e.message}", Toast.LENGTH_LONG).show()
                    onComplete()
                }
            }
        }
    }

    private data class PlanOption(
        val planId: String,
        val displayName: String,
        val offerId: String?,
        val isCurrentPlan: Boolean = false,
    )

    private data class ReplacementModeOption(
        val mode: SubscriptionReplacementMode,
        val displayName: String,
        val description: String,
    )
}

@ContributesMultibinding(ActivityScope::class)
class SwitchSubscriptionViewPlugin @Inject constructor() : SubsSettingPlugin {
    override fun getView(context: Context): View = SwitchSubscriptionView(context)
}
