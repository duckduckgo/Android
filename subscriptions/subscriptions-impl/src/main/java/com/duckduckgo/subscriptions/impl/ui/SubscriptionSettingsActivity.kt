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
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.PrivacyProFeedbackScreenWithParams
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.PrivacyProFeedbackSource.SUBSCRIPTION_SETTINGS
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.impl.R.*
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BASIC_SUBSCRIPTION
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.FAQS_URL
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionSettingsBinding
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.ui.ChangePlanActivity.Companion.ChangePlanScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsActivity.Companion.SubscriptionsSettingsScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.FinishSignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToAddEmailScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToEditEmailScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToPortal
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Monthly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Yearly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.ViewState
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams.ToolbarConfig.CustomTitle
import javax.inject.Inject
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionsSettingsScreenWithEmptyParams::class, screenName = "ppro.settings")
class SubscriptionSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var pixelSender: SubscriptionPixelSender

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

        binding.removeDevice.setClickListener {
            TextAlertDialogBuilder(this)
                .setTitle(string.removeFromDevice)
                .setMessage(string.removeFromDeviceDescription)
                .setDestructiveButtons(true)
                .setPositiveButton(string.removeSubscription)
                .setNegativeButton(string.cancel)
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

        binding.manageEmail.setOnClickListener {
            viewModel.onEmailButtonClicked()
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
        if (viewState.status in listOf(INACTIVE, EXPIRED)) {
            binding.viewPlans.isVisible = true
            binding.changePlan.isVisible = false
            binding.subscriptionActiveStatusContainer.isVisible = false
            binding.subscriptionExpiredStatusContainer.isVisible = true
            binding.subscriptionExpiredStatusText.text = getString(string.subscriptionsExpiredData, viewState.date)
        } else {
            binding.viewPlans.isVisible = false
            binding.changePlan.isVisible = true
            binding.subscriptionActiveStatusContainer.isVisible = true
            binding.subscriptionExpiredStatusContainer.isVisible = false

            val status = when (viewState.status) {
                AUTO_RENEWABLE -> getString(string.renews)
                else -> getString(string.expires)
            }

            val subscriptionsDataStringResId = when (viewState.duration) {
                Monthly -> string.subscriptionsDataMonthly
                Yearly -> string.subscriptionsDataYearly
            }

            binding.changePlan.setSecondaryText(getString(subscriptionsDataStringResId, status, viewState.date))

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
            binding.manageEmail.setPrimaryText(resources.getString(string.addEmailPrimaryText))
            binding.manageEmail.setSecondaryText(resources.getString(string.addEmailSecondaryText))
        } else {
            binding.manageEmail.setPrimaryText(resources.getString(string.editEmailPrimaryText))
            binding.manageEmail.setSecondaryText(viewState.email + "\n\n" + resources.getString(string.editEmailSecondaryText))
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is FinishSignOut -> {
                Toast.makeText(this, string.subscriptionRemoved, Toast.LENGTH_SHORT).show()
                finish()
            }

            GoToAddEmailScreen -> goToAddEmail()
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
        }
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
                url = SubscriptionsConstants.BUY_URL,
            ),
        )
    }

    private fun goToEditEmail() {
        globalActivityStarter.start(
            this,
            SubscriptionsWebViewActivityWithParams(
                url = MANAGE_URL,
                toolbarConfig = CustomTitle(getString(string.manageEmail)),
            ),
        )
    }

    private fun goToAddEmail() {
        globalActivityStarter.start(
            this,
            SubscriptionsWebViewActivityWithParams(
                url = ADD_EMAIL_URL,
                toolbarConfig = CustomTitle(getString(string.addEmailText)),
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

    companion object {
        const val URL = "https://play.google.com/store/account/subscriptions?sku=%s&package=%s"
        const val ADD_EMAIL_URL = "https://duckduckgo.com/subscriptions/add-email"
        const val MANAGE_URL = "https://duckduckgo.com/subscriptions/manage"
        const val LEARN_MORE_URL = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/adding-email"
        const val PRIVACY_POLICY_URL = "https://duckduckgo.com/pro/privacy-terms"

        data object SubscriptionsSettingsScreenWithEmptyParams : GlobalActivityStarter.ActivityParams
    }
}
