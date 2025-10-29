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

package com.duckduckgo.subscriptions.impl.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.ActiveOfferType
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.PrivacyProFeedbackScreenWithParams
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.SUBSCRIPTION_SETTINGS
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionsSettingsScreenWithEmptyParams
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.impl.R.*
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BASIC_SUBSCRIPTION
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.FAQS_URL
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionSettingsBinding
import com.duckduckgo.subscriptions.impl.internal.SubscriptionsUrlProvider
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.switch_plan.SwitchPlanBottomSheetDialogFactory
import com.duckduckgo.subscriptions.impl.ui.ChangePlanActivity.Companion.ChangePlanScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.DismissRebrandingBanner
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.FinishSignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToActivationScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToEditEmailScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToPortal
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.ShowSwitchPlanDialog
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Monthly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Yearly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SwitchPlanType
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.ViewState
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams.ToolbarConfig.CustomTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionsSettingsScreenWithEmptyParams::class, screenName = "ppro.settings")
class SubscriptionSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var pixelSender: SubscriptionPixelSender

    @Inject
    lateinit var subscriptionsUrlProvider: SubscriptionsUrlProvider

    @Inject
    lateinit var switchPlanDialogFactory: SwitchPlanBottomSheetDialogFactory

    private val viewModel: SubscriptionSettingsViewModel by bindViewModel()
    private val binding: ActivitySubscriptionSettingsBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)

        lifecycle.addObserver(viewModel)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .filterIsInstance(ViewState.Ready::class)
            .onEach { renderView(it) }
            .launchIn(lifecycleScope)

        binding.manageEmail.setOnClickListener {
            viewModel.onEditEmailButtonClicked()
        }

        binding.addToDevice.setOnClickListener {
            viewModel.onAddToDeviceButtonClicked()
        }

        binding.faq.setClickListener {
            goToFaqs()
        }
        binding.sendFeedback.setOnClickListener {
            goToFeedback()
        }

        binding.learnMore.setOnClickListener {
            goToLearnMore()
        }

        binding.viewPlans.setClickListener {
            goToPurchasePage()
        }

        binding.privacyPolicy.setOnClickListener {
            goToPrivacyPolicy()
        }

        if (savedInstanceState == null) {
            pixelSender.reportSubscriptionSettingsShown()
        }
    }

    private fun goToFeedback() {
        globalActivityStarter.start(
            this,
            PrivacyProFeedbackScreenWithParams(
                feedbackSource = SUBSCRIPTION_SETTINGS,
            ),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(viewModel)
    }

    private fun renderView(viewState: ViewState.Ready) {
        binding.subscriptionSettingsProductName.setText(string.ddg_subscription)
        binding.activateOnOtherDevices.setText(string.activateOnOtherDevices)
        binding.faq.setPrimaryText(getString(string.privacyProFaq))
        binding.faq.setSecondaryText(getString(string.privacyProFaqSecondary))

        if (viewState.showRebrandingBanner) {
            binding.includePrivacyProRebrandingBanner.root.show()
            binding.includePrivacyProRebrandingBanner.settingsBannerClose.setOnClickListener {
                viewModel.rebrandingBannerDismissed()
            }
        } else {
            binding.includePrivacyProRebrandingBanner.root.gone()
        }

        if (viewState.status in listOf(INACTIVE, EXPIRED)) {
            binding.viewPlans.isVisible = true
            binding.changePlan.isVisible = false
            binding.switchPlan.isVisible = false
            binding.subscriptionActiveStatusContainer.isVisible = false
            binding.subscriptionExpiredStatusContainer.isVisible = true
            binding.subscriptionExpiredStatusText.text = getString(string.subscriptionsExpiredData, viewState.date)
        } else {
            binding.viewPlans.isVisible = false
            binding.changePlan.isVisible = true
            binding.subscriptionActiveStatusContainer.isVisible = true
            binding.subscriptionExpiredStatusContainer.isVisible = false

            // Show switch plan option if available (Android only)
            if (viewState.switchPlanAvailable && viewState.platform.lowercase() == "google") {
                binding.switchPlan.show()
                val switchText = when (viewState.duration) {
                    Monthly -> getString(string.subscriptionSettingSwitchUpgrade)
                    Yearly -> getString(string.subscriptionSettingSwitchDowngrade)
                }
                binding.switchPlan.setPrimaryText(switchText)
                binding.switchPlan.setClickListener {
                    viewModel.onSwitchPlanClicked(viewState.duration)
                }
            } else {
                binding.switchPlan.gone()
            }

            // Free Trial active
            if (viewState.activeOffers.contains(ActiveOfferType.TRIAL)) {
                binding.subscriptionActiveStatusTextView.text = getString(string.subscriptionStatusFreeTrial)

                val subscriptionRenewalDetailsRes = when {
                    viewState.status == AUTO_RENEWABLE && viewState.duration == Monthly ->
                        getString(string.freeTrialMonthlyActiveSubscriptionsData, viewState.date)
                    viewState.status == AUTO_RENEWABLE && viewState.duration == Yearly ->
                        getString(string.freeTrialYearlyActiveSubscriptionsData, viewState.date)
                    else -> getString(string.freeTrialCancelledSubscriptionsData, viewState.date)
                }
                binding.changePlan.setSecondaryText(subscriptionRenewalDetailsRes)

                // Active status without a Free Trial
            } else {
                binding.subscriptionActiveStatusTextView.text = getString(string.subscriptionStatusSubscribed)

                val status = when (viewState.status) {
                    AUTO_RENEWABLE -> getString(string.renews)
                    else -> getString(string.expires)
                }

                val subscriptionsDataStringResId = when (viewState.duration) {
                    Monthly -> string.subscriptionsDataMonthly
                    Yearly -> string.subscriptionsDataYearly
                }

                binding.changePlan.setSecondaryText(getString(subscriptionsDataStringResId, status, viewState.date))
            }

            when (viewState.platform.lowercase()) {
                "apple", "ios" ->
                    binding.changePlan.setClickListener {
                        pixelSender.reportSubscriptionSettingsChangePlanOrBillingClick()
                        globalActivityStarter.start(this, ChangePlanScreenWithEmptyParams)
                    }

                "stripe" -> {
                    binding.changePlan.setClickListener {
                        pixelSender.reportSubscriptionSettingsChangePlanOrBillingClick()
                        viewModel.goToStripe()
                    }
                }

                else -> {
                    binding.changePlan.setClickListener {
                        pixelSender.reportSubscriptionSettingsChangePlanOrBillingClick()
                        val url = String.format(URL, BASIC_SUBSCRIPTION, applicationContext.packageName)
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setData(Uri.parse(url))
                        startActivity(intent)
                    }
                }
            }
        }

        if (viewState.email == null) {
            binding.manageEmail.gone()
            binding.addToDevice.setSecondaryText(resources.getString(string.addToDeviceSecondaryTextWithoutEmail))
        } else {
            binding.manageEmail.show()
            binding.manageEmail.setSecondaryText(viewState.email)
            binding.addToDevice.setSecondaryText(resources.getString(string.addToDeviceSecondaryTextWithEmail))
        }

        if (viewState.showFeedback) {
            binding.sendFeedback.show()
        } else {
            binding.sendFeedback.gone()
        }

        binding.removeDevice.setClickListener {
            TextAlertDialogBuilder(this)
                .setTitle(string.removeFromDevice)
                .setMessage(string.removeFromDeviceDescription)
                .setPositiveButton(string.removeSubscription, DESTRUCTIVE)
                .setNegativeButton(string.cancel, GHOST_ALT)
                .addEventListener(
                    object : TextAlertDialogBuilder.EventListener() {
                        override fun onPositiveButtonClicked() {
                            viewModel.removeFromDevice()
                        }

                        override fun onNegativeButtonClicked() {
                            // NOOP
                        }
                    },
                )
                .show()
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is FinishSignOut -> {
                Toast.makeText(this, string.subscriptionRemoved, Toast.LENGTH_SHORT).show()
                finish()
            }

            GoToActivationScreen -> goToActivation()
            GoToEditEmailScreen -> goToEditEmail()

            is GoToPortal -> {
                globalActivityStarter.start(
                    this,
                    SubscriptionsWebViewActivityWithParams(
                        url = command.url,
                        toolbarConfig = CustomTitle(getString(string.changePlanTitle)),
                    ),
                )
            }

            is DismissRebrandingBanner -> dismissRebrandingBanner()
            
            is ShowSwitchPlanDialog -> showSwitchPlanDialog(command.switchType)
        }
    }

    private fun showSwitchPlanDialog(switchType: SwitchPlanType) {
        val dialog = switchPlanDialogFactory.create(
            context = this,
            lifecycleOwner = this,
            switchType = switchType,
        )
        dialog.show()
    }

    private fun goToFaqs() {
        globalActivityStarter.start(
            this,
            SubscriptionsWebViewActivityWithParams(
                url = FAQS_URL,
                toolbarConfig = CustomTitle(""), // empty toolbar
            ),
        )
    }

    private fun goToLearnMore() {
        globalActivityStarter.start(
            context = this,
            params = SubscriptionsWebViewActivityWithParams(
                url = LEARN_MORE_URL,
                toolbarConfig = CustomTitle(""), // empty toolbar
            ),
        )
    }

    private fun goToPurchasePage() {
        globalActivityStarter.start(
            context = this,
            params = SubscriptionsWebViewActivityWithParams(
                url = subscriptionsUrlProvider.buyUrl,
            ),
        )
    }

    private fun goToEditEmail() {
        globalActivityStarter.start(
            this,
            SubscriptionsWebViewActivityWithParams(
                url = subscriptionsUrlProvider.manageUrl,
                toolbarConfig = CustomTitle(getString(string.manageEmail)),
            ),
        )
    }

    private fun goToActivation() {
        globalActivityStarter.start(
            this,
            SubscriptionsWebViewActivityWithParams(
                url = subscriptionsUrlProvider.activateUrl,
            ),
        )
    }

    private fun goToPrivacyPolicy() {
        globalActivityStarter.start(
            this,
            WebViewActivityWithParams(
                url = PRIVACY_POLICY_URL,
                screenTitle = getString(string.privacyPolicyAndTermsOfService),
            ),
        )
    }

    private fun dismissRebrandingBanner() {
        binding.includePrivacyProRebrandingBanner.root.gone()
    }

    companion object {
        const val URL = "https://play.google.com/store/account/subscriptions?sku=%s&package=%s"

        // const val MANAGE_URL = "https://duckduckgo.com/subscriptions/manage"
        const val LEARN_MORE_URL = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/adding-email"
        const val PRIVACY_POLICY_URL = "https://duckduckgo.com/pro/privacy-terms"
    }
}
