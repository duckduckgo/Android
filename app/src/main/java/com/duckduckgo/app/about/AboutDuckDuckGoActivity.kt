/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.about

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.Command
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityAboutDuckDuckGoBinding
import com.duckduckgo.app.global.AppUrl.Url
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.browser.api.ui.WebViewActivityWithParams
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetPWaitlistInvitedScreenNoParams
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AboutScreenNoParams::class)
class AboutDuckDuckGoActivity : DuckDuckGoActivity() {

    private val viewModel: AboutDuckDuckGoViewModel by bindViewModel()
    private val binding: ActivityAboutDuckDuckGoBinding by viewBinding()

    private val feedbackFlow = registerForActivityResult(FeedbackContract()) { resultOk ->
        if (resultOk) {
            Snackbar.make(
                binding.root,
                R.string.thanksForTheFeedback,
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()

        viewModel.resetNetPEasterEggCounter()
    }

    private fun configureUiEventHandlers() {
        binding.includeContent.learnMoreLink.setOnClickListener {
            viewModel.onLearnMoreLinkClicked()
        }

        binding.includeContent.aboutPrivacyPolicy.setClickListener {
            viewModel.onPrivacyPolicyClicked()
        }

        binding.includeContent.aboutVersion.setClickListener {
            viewModel.onVersionClicked()
        }

        binding.includeContent.aboutProvideFeedback.setClickListener {
            viewModel.onProvideFeedbackClicked()
        }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState.let {
                    binding.includeContent.aboutVersion.setSecondaryText(it.version)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun launchNetPWaitlist() {
        globalActivityStarter.start(this, NetPWaitlistInvitedScreenNoParams)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is Command.LaunchBrowserWithLearnMoreUrl -> launchBrowserScreen()
            is Command.LaunchWebViewWithPrivacyPolicyUrl -> launchWebViewScreen()
            is Command.ShowNetPUnlockedSnackbar -> showNetPUnlockedSnackbar()
            is Command.LaunchNetPWaitlist -> launchNetPWaitlist()
            is Command.LaunchFeedback -> launchFeedback()
        }
    }

    private fun launchBrowserScreen() {
        startActivity(BrowserActivity.intent(this, Url.ABOUT))
        finish()
    }

    private fun launchWebViewScreen() {
        globalActivityStarter.start(
            this,
            WebViewActivityWithParams(
                url = PRIVACY_POLICY_WEB_LINK,
                screenTitle = getString(R.string.settingsPrivacyPolicyDuckduckgo),
            ),
        )
    }

    private fun showNetPUnlockedSnackbar() {
        Snackbar.make(
            binding.root,
            R.string.netpUnlockedSnackbar,
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.netpUnlockedSnackbarAction) {
            viewModel.onNetPUnlockedActionClicked()
        }.setDuration(3500) // LENGTH_LONG is not long enough, increase to 3.5 sec
            .show()
    }

    private fun launchFeedback() {
        feedbackFlow.launch(null)
    }

    companion object {
        private const val PRIVACY_POLICY_WEB_LINK = "https://duckduckgo.com/privacy"
    }
}
