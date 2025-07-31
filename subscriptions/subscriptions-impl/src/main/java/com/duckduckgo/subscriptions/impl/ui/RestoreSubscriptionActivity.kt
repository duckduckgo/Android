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

import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.subscriptions.api.SubscriptionScreens.RestoreSubscriptionScreenWithParams
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionsSettingsScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.R.string
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ACTIVATE_URL
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BUY_URL
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.WELCOME_URL
import com.duckduckgo.subscriptions.impl.databinding.ActivityRestoreSubscriptionBinding
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.Error
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.FinishAndGoToOnboarding
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.FinishAndGoToSubscriptionSettings
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.RestoreFromEmail
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.SubscriptionNotFound
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.Success
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.ViewState
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(RestoreSubscriptionScreenWithParams::class)
class RestoreSubscriptionActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: RestoreSubscriptionViewModel by bindViewModel()
    private val binding: ActivityRestoreSubscriptionBinding by viewBinding()

    private var isOriginWeb = true
    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val params = intent.getActivityParams(RestoreSubscriptionScreenWithParams::class.java)
        isOriginWeb = params?.isOriginWeb ?: true

        setContentView(binding.root)
        setupToolbar(toolbar)

        viewModel.init()

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        viewModel.viewState
            .onEach { renderView(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderView(viewState: ViewState) {
        val title: Int
        val secondaryText: Int
        if (viewState.rebrandingEnabled) {
            title = string.restoreSubscriptionTitleRebranding
            secondaryText = string.restoreSubscriptionEmailDescriptionRebranding
        } else {
            title = string.restoreSubscriptionTitle
            secondaryText = string.restoreSubscriptionEmailDescription
        }
        binding.title.setText(title)
        binding.restoreSubscriptionEmailTitle.setSecondaryText(getString(secondaryText))

        // removing the click listeners from the LineListItems
        // so that they don't trigger the selectable background animation when interacted with
        binding.restoreSubscriptionEmailTitle.setOnClickListener(null)
        binding.restoreSubscriptionGooglePlayTitle.setOnClickListener(null)

        binding.restoreSubscriptionEmailLayout.setOnClickListener {
            viewModel.restoreFromEmail()
        }
        binding.restoreSubscriptionGooglePlayLayout.setOnClickListener {
            viewModel.restoreFromStore()
        }
    }

    private val startForResultRestore = registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onSubscriptionRestoredFromEmail()
        }
    }

    private fun goToRestore() {
        val intent = globalActivityStarter.startIntent(
            this,
            SubscriptionsWebViewActivityWithParams(
                url = ACTIVATE_URL,
            ),
        )
        startForResultRestore.launch(intent)
    }

    private fun onPurchaseRestored() {
        TextAlertDialogBuilder(this)
            .setTitle(string.youAreSet)
            .setMessage(string.purchaseRestored)
            .setPositiveButton(string.ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        if (isOriginWeb) {
                            setResult(RESULT_OK)
                        } else {
                            goToSubscriptionsWelcomePage()
                        }
                        finish()
                    }
                },
            )
            .show()
    }

    private fun goToSubscriptions() {
        startSubscriptionsWebViewActivity(url = BUY_URL)
    }

    private fun goToSubscriptionsWelcomePage() {
        startSubscriptionsWebViewActivity(url = WELCOME_URL)
    }

    private fun startSubscriptionsWebViewActivity(url: String) {
        globalActivityStarter.start(
            context = this,
            params = SubscriptionsWebViewActivityWithParams(url),
        )
    }

    private fun subscriptionNotFound() {
        TextAlertDialogBuilder(this)
            .setTitle(string.subscriptionNotFound)
            .setMessage(string.subscriptionNotFoundDescription)
            .setPositiveButton(string.viewPlans)
            .setNegativeButton(string.cancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        goToSubscriptions()
                    }
                },
            )
            .show()
    }

    private fun showError() {
        TextAlertDialogBuilder(this)
            .setTitle(string.somethingWentWrong)
            .setMessage(string.somethingWentWrongDescription)
            .setPositiveButton(string.ok)
            .show()
    }

    private fun finishAndGoToOnboarding() {
        if (isOriginWeb) {
            setResult(RESULT_OK)
        } else {
            goToSubscriptionsWelcomePage()
        }
        finish()
    }

    private fun finishAndGoToSubscriptionSettings() {
        if (isOriginWeb) {
            setResult(RESULT_OK)
        }
        globalActivityStarter.start(this, SubscriptionsSettingsScreenWithEmptyParams)
        finish()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is RestoreFromEmail -> goToRestore()
            is Success -> onPurchaseRestored()
            is SubscriptionNotFound -> subscriptionNotFound()
            is Error -> showError()
            is FinishAndGoToOnboarding -> finishAndGoToOnboarding()
            is FinishAndGoToSubscriptionSettings -> finishAndGoToSubscriptionSettings()
        }
    }
}
