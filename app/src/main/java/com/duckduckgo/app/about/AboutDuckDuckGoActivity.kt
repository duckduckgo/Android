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
import android.text.Annotation
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.Command
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.Command.LaunchBrowserWithLearnMoreUrl
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.Command.LaunchBrowserWithPrivacyProtectionsUrl
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.Command.LaunchFeedback
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.Command.LaunchPproUnifiedFeedback
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.Command.LaunchWebViewWithComparisonChartUrl
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.Command.LaunchWebViewWithPPROUrl
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.Command.LaunchWebViewWithPrivacyPolicyUrl
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.Command.LaunchWebViewWithVPNUrl
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityAboutDuckDuckGoBinding
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.AppUrl.Url
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.R.attr
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.GeneralPrivacyProFeedbackScreenNoParams
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AboutScreenNoParams::class)
class AboutDuckDuckGoActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var settingsPageFeature: SettingsPageFeature

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

        if (settingsPageFeature.newSettingsPage().isEnabled()) {
            supportActionBar?.setTitle(R.string.aboutActivityTitleNew)
            binding.includeContent.aboutTextNew.isVisible = true

            binding.includeContent.aboutText.isGone = true
            binding.includeContent.aboutProvideFeedback.isGone = true
        }

        configureUiEventHandlers()
        observeViewModel()
        configureClickableLinks()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resetEasterEggCounter()
    }

    private fun configureClickableLinks() {
        if (settingsPageFeature.newSettingsPage().isEnabled()) {
            with(binding.includeContent.aboutTextNew) {
                text = addClickableLinks()
                movementMethod = LinkMovementMethod.getInstance()
            }
        } else {
            with(binding.includeContent.aboutText) {
                text = addClickableLinks()
                movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }

    private fun addClickableLinks(): SpannableString {
        val fullText = getText(
            if (settingsPageFeature.newSettingsPage().isEnabled()) {
                R.string.aboutDescriptionBrandUpdate2025
            } else {
                R.string.aboutDescriptionBrandUpdate2025
            },
        ) as SpannedString

        val spannableString = SpannableString(fullText)
        val annotations = fullText.getSpans(0, fullText.length, Annotation::class.java)

        annotations?.find { it.value == COMPARISON_CHART_ANNOTATION }?.let {
            addSpannable(spannableString, fullText, it) {
                viewModel.onComparisonChartLinkClicked()
            }
        }

        annotations?.find { it.value == PPRO_ANNOTATION }?.let {
            addSpannable(spannableString, fullText, it) {
                viewModel.onPProHelpPageLinkClicked()
            }
        }

        annotations?.find { it.value == VPN_ANNOTATION }?.let {
            addSpannable(spannableString, fullText, it) {
                viewModel.onVPNHelpPageLinkClicked()
            }
        }

        annotations?.find { it.value == PRIVACY_PROTECTION_ANNOTATION }?.let {
            addSpannable(spannableString, fullText, it) {
                viewModel.onPrivacyProtectionsLinkClicked()
            }
        }

        annotations?.find { it.value == LEARN_MORE_ANNOTATION }?.let {
            addSpannable(spannableString, fullText, it) {
                viewModel.onLearnMoreLinkClicked()
            }
        }

        return spannableString
    }

    private fun addSpannable(
        spannableString: SpannableString,
        fullText: SpannedString,
        it: Annotation,
        onClick: (widget: View) -> Unit,
    ) {
        spannableString.apply {
            setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        onClick(widget)
                    }
                },
                fullText.getSpanStart(it),
                fullText.getSpanEnd(it),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                UnderlineSpan(),
                fullText.getSpanStart(it),
                fullText.getSpanEnd(it),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                ForegroundColorSpan(
                    getColorFromAttr(attr.daxColorAccentBlue),
                ),
                fullText.getSpanStart(it),
                fullText.getSpanEnd(it),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun configureUiEventHandlers() {
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

    private fun processCommand(it: Command) {
        when (it) {
            LaunchBrowserWithLearnMoreUrl -> launchBrowserScreen()
            LaunchWebViewWithPrivacyPolicyUrl -> launchWebViewScreen(PRIVACY_POLICY_WEB_LINK, getString(R.string.settingsPrivacyPolicyDuckduckgo))
            LaunchBrowserWithPrivacyProtectionsUrl -> launchPrivacyProtectionsScreen()
            LaunchFeedback -> launchFeedback()
            LaunchPproUnifiedFeedback -> launchPproUnifiedFeedback()
            LaunchWebViewWithComparisonChartUrl -> launchWebViewScreen(COMPARISON_CHART_URL, getString(R.string.settingsAboutDuckduckgo))
            LaunchWebViewWithPPROUrl -> launchWebViewScreen(PPRO_URL, getString(R.string.settingsAboutDuckduckgo))
            LaunchWebViewWithVPNUrl -> launchWebViewScreen(VPN_URL, getString(R.string.settingsAboutDuckduckgo))
        }
    }

    private fun launchBrowserScreen() {
        startActivity(BrowserActivity.intent(this, Url.ABOUT, interstitialScreen = true))
        finish()
    }

    private fun launchWebViewScreen(url: String, screenTitle: String) {
        globalActivityStarter.start(
            this,
            WebViewActivityWithParams(
                url = url,
                screenTitle = screenTitle,
            ),
        )
    }

    private fun launchPrivacyProtectionsScreen() {
        globalActivityStarter.start(
            this,
            WebViewActivityWithParams(
                url = PRIVACY_PROTECTIONS_WEB_LINK,
                screenTitle = getString(R.string.settingsAboutDuckduckgo),
            ),
        )
    }

    private fun launchFeedback() {
        feedbackFlow.launch(null)
    }

    private fun launchPproUnifiedFeedback() {
        globalActivityStarter.start(
            this,
            GeneralPrivacyProFeedbackScreenNoParams,
        )
    }

    companion object {
        private const val PRIVACY_PROTECTION_ANNOTATION = "privacy_protection_link"
        private const val LEARN_MORE_ANNOTATION = "learn_more_link"
        private const val PRIVACY_POLICY_WEB_LINK = "https://duckduckgo.com/privacy"
        private const val PRIVACY_PROTECTIONS_WEB_LINK = "https://duckduckgo.com/duckduckgo-help-pages/privacy/web-tracking-protections/"
        private const val COMPARISON_CHART_ANNOTATION = "chart_comparison"
        private const val COMPARISON_CHART_URL = "https://duckduckgo.com/compare-privacy"
        private const val PPRO_ANNOTATION = "ppro_help_page"
        private const val PPRO_URL = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/"
        private const val VPN_ANNOTATION = "vpn_help_page"
        private const val VPN_URL = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/vpn/"
    }
}
