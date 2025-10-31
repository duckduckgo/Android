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
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.FrameLayout
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
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.launch
import logcat.logcat
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.R as MaterialR

@SuppressLint("NoBottomSheetDialog")
class SwitchPlanBottomSheetDialog @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val lifecycleOwner: LifecycleOwner,
    @Assisted private val switchType: SwitchPlanType,
    @Assisted private val onSwitchSuccess: () -> Unit,
    private val subscriptionsManager: SubscriptionsManager,
    private val dispatcherProvider: DispatcherProvider,
) : BottomSheetDialog(context) {

    private val binding: BottomSheetSwitchPlanBinding = BottomSheetSwitchPlanBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)
        // We need the dialog to always be expanded and not draggable because the content takes up a lot of vertical space and requires a scroll view,
        // especially in landscape aspect-ratios. If the dialog started as collapsed, the drag would interfere with internal scroll.
        this.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        this.behavior.isDraggable = false

        setOnShowListener { dialogInterface ->
            setRoundCorners(dialogInterface)
        }
    }

    /**
     * By default, when bottom sheet dialog is expanded, the corners become squared.
     * This function ensures that the bottom sheet dialog will have rounded corners even when in an expanded state.
     */
    private fun setRoundCorners(dialogInterface: DialogInterface) {
        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(MaterialR.id.design_bottom_sheet)

        val shapeDrawable = MaterialShapeDrawable.createWithElevationOverlay(context)
        shapeDrawable.shapeAppearanceModel = shapeDrawable.shapeAppearanceModel
            .toBuilder()
            .setTopLeftCorner(CornerFamily.ROUNDED, context.resources.getDimension(CommonR.dimen.dialogBorderRadius))
            .setTopRightCorner(CornerFamily.ROUNDED, context.resources.getDimension(CommonR.dimen.dialogBorderRadius))
            .build()
        bottomSheet?.background = shapeDrawable
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        configureViews()
        observePurchaseState()
    }

    private fun configureViews() {
        binding.switchBottomSheetDialogCloseButton.setOnClickListener {
            dismiss()
        }

        lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io()) {
            val isUpgrade = switchType == SwitchPlanType.UPGRADE_TO_YEARLY
            val pricingInfo = subscriptionsManager.getSwitchPlanPricing(isUpgrade)

            launch(dispatcherProvider.main()) {
                when (switchType) {
                    SwitchPlanType.UPGRADE_TO_YEARLY -> {
                        // Configure for upgrade (Monthly → Yearly)
                        binding.switchBottomSheetDialogTitle.text = context.getString(R.string.switchBottomSheetTitleUpgrade)
                        binding.switchBottomSheetDialogSubTitle.text = context.getString(
                            R.string.switchBottomSheetDescriptionUpgrade,
                            pricingInfo?.yearlyMonthlyEquivalent ?: "",
                            pricingInfo?.currentPrice ?: "",
                        )
                        binding.switchBottomSheetDialogPrimaryButton.text = context.getString(
                            R.string.switchBottomSheetPrimaryButtonUpgrade,
                            pricingInfo?.targetPrice ?: "",
                        )
                        binding.switchBottomSheetDialogSecondaryButton.text = context.getString(R.string.switchBottomSheetSecondaryButtonUpgrade)

                        binding.switchBottomSheetDialogPrimaryButton.setOnClickListener {
                            triggerSwitch(isUpgrade = true)
                            dismiss()
                        }
                        binding.switchBottomSheetDialogSecondaryButton.setOnClickListener {
                            dismiss()
                        }
                    }

                    SwitchPlanType.DOWNGRADE_TO_MONTHLY -> {
                        // Configure for downgrade (Yearly → Monthly)
                        binding.switchBottomSheetDialogTitle.text = context.getString(R.string.switchBottomSheetTitleDowngrade)
                        binding.switchBottomSheetDialogSubTitle.text = context.getString(
                            R.string.switchBottomSheetDescriptionDowngrade,
                            pricingInfo?.yearlyMonthlyEquivalent ?: "",
                            pricingInfo?.targetPrice ?: "",
                        )
                        binding.switchBottomSheetDialogPrimaryButton.text = context.getString(R.string.switchBottomSheetPrimaryButtonDowngrade)
                        binding.switchBottomSheetDialogSecondaryButton.text = context.getString(
                            R.string.switchBottomSheetSecondaryButtonDowngrade,
                            pricingInfo?.targetPrice ?: "",
                        )

                        binding.switchBottomSheetDialogPrimaryButton.setOnClickListener {
                            dismiss()
                        }
                        binding.switchBottomSheetDialogSecondaryButton.setOnClickListener {
                            triggerSwitch(isUpgrade = false)
                            dismiss()
                        }
                    }
                }
            }
        }
    }

    private fun observePurchaseState() {
        lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io()) {
            subscriptionsManager.currentPurchaseState.collect {
                when (it) {
                    is CurrentPurchase.Success -> {
                        logcat { "Switch flow: Successfully switched plans" }
                        onSwitchSuccess.invoke()
                    }

                    is CurrentPurchase.Failure -> {
                        logcat { "Switch flow: Failed to switch plans. Error: ${it.message}" }
                    }

                    is CurrentPurchase.Canceled -> {
                        logcat { "Switch flow: Canceled switch plans" }
                    }

                    else -> {}
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
                        logcat { "Switch flow: Failed to switch plans. No active subscription found" }
                        dismiss()
                    }
                    return@launch
                }

                // Determine target plan based on current subscription
                val isUS = subscription.productId in listOf(MONTHLY_PLAN_US, YEARLY_PLAN_US)
                val targetPlanId = if (isUpgrade) {
                    if (isUS) YEARLY_PLAN_US else YEARLY_PLAN_ROW
                } else {
                    if (isUS) MONTHLY_PLAN_US else MONTHLY_PLAN_ROW
                }

                launch(dispatcherProvider.main()) {
                    subscriptionsManager.switchSubscriptionPlan(
                        activity = context as Activity,
                        planId = targetPlanId,
                        offerId = null,
                        replacementMode = SubscriptionReplacementMode.WITHOUT_PRORATION,
                    )
                }
            } catch (e: Exception) {
                logcat { "Switch flow: Failed to switch plans. Exception: ${e.message}" }
            }
        }
    }
}
