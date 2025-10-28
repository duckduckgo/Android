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

package com.duckduckgo.subscriptions.impl.switch_plan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.subscriptions.impl.CurrentPurchase
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.billing.SubscriptionReplacementMode
import com.duckduckgo.subscriptions.impl.databinding.BottomSheetSwitchPlanBinding
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SwitchPlanType
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

@SuppressLint("NoBottomSheetDialog")
class SwitchPlanBottomSheetDialog(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val subscriptionsManager: SubscriptionsManager,
    private val dispatcherProvider: DispatcherProvider,
    private val switchType: SwitchPlanType,
) : BottomSheetDialog(context) {

    private val binding: BottomSheetSwitchPlanBinding = BottomSheetSwitchPlanBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)
        this.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        this.behavior.isDraggable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        configureViews()
        lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io()) {
            subscriptionsManager.currentPurchaseState.collect {
                when (it) {
                    is CurrentPurchase.Success -> {
                        launch(dispatcherProvider.main()) {
                            Toast.makeText(context, context.getString(R.string.switchPlanSuccessMessage), Toast.LENGTH_LONG).show()
                            dismiss()
                        }
                    }
                    is CurrentPurchase.Failure -> {
                        launch(dispatcherProvider.main()) {
                            Toast.makeText(context, context.getString(R.string.switchPlanErrorMessage), Toast.LENGTH_LONG).show()
                        }
                    }
                    is CurrentPurchase.Canceled -> {
                        launch(dispatcherProvider.main()) {
                            dismiss()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun configureViews() {
        binding.switchBottomSheetDialogCloseButton.setOnClickListener {
            dismiss()
        }
        when (switchType) {
            SwitchPlanType.UPGRADE_TO_YEARLY -> {
                binding.switchBottomSheetDialogTitle.text = context.getString(R.string.switchBottomSheetTitleUpgrade)
                binding.switchBottomSheetDialogSubTitle.text = context.getString(R.string.switchBottomSheetDescriptionUpgrade)
                binding.switchBottomSheetDialogPrimaryButton.text = context.getString(R.string.switchBottomSheetPrimaryButtonUpgrade)
                binding.switchBottomSheetDialogSecondaryButton.text = context.getString(R.string.switchBottomSheetSecondaryButtonUpgrade)
                binding.switchBottomSheetDialogPrimaryButton.setOnClickListener {
                    triggerSwitch(isUpgrade = true)
                }
                binding.switchBottomSheetDialogSecondaryButton.setOnClickListener {
                    dismiss()
                }
            }
            SwitchPlanType.DOWNGRADE_TO_MONTHLY -> {
                binding.switchBottomSheetDialogTitle.text = context.getString(R.string.switchBottomSheetTitleDowngrade)
                binding.switchBottomSheetDialogSubTitle.text = context.getString(R.string.switchBottomSheetDescriptionDowngrade)
                binding.switchBottomSheetDialogPrimaryButton.text = context.getString(R.string.switchBottomSheetPrimaryButtonDowngrade)
                binding.switchBottomSheetDialogSecondaryButton.text = context.getString(R.string.switchBottomSheetSecondaryButtonDowngrade)
                binding.switchBottomSheetDialogPrimaryButton.setOnClickListener {
                    dismiss()
                }
                binding.switchBottomSheetDialogSecondaryButton.setOnClickListener {
                    triggerSwitch(isUpgrade = false)
                }
            }
        }
    }

    private fun triggerSwitch(isUpgrade: Boolean) {
        lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io()) {
            try {
                val subscription = subscriptionsManager.getSubscription()
                if (subscription == null) {
                    launch(dispatcherProvider.main()) {
                        Toast.makeText(context, "No active subscription found", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                    return@launch
                }
                val isUS = subscription.productId in listOf(MONTHLY_PLAN_US, YEARLY_PLAN_US)
                val targetPlanId = if (isUpgrade) {
                    if (isUS) YEARLY_PLAN_US else YEARLY_PLAN_ROW
                } else {
                    if (isUS) MONTHLY_PLAN_US else MONTHLY_PLAN_ROW
                }
                val replacementMode = if (isUpgrade) {
                    SubscriptionReplacementMode.CHARGE_PRORATED_PRICE
                } else {
                    SubscriptionReplacementMode.DEFERRED
                }
                launch(dispatcherProvider.main()) {
                    subscriptionsManager.switchSubscriptionPlan(
                        activity = context as Activity,
                        planId = targetPlanId,
                        offerId = null,
                        replacementMode = replacementMode,
                    )
                }
            } catch (e: Exception) {
                launch(dispatcherProvider.main()) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
