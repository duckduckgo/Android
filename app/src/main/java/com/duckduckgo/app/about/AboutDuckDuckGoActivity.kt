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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.about.AboutDuckDuckGoViewModel.Command
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
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.GeneralPrivacyProFeedbackScreenNoParams
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
        supportActionBar?.setTitle(R.string.aboutActivityTitleNew)

        configureUiEventHandlers()
        observeViewModel()
        configureClickableLinks()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resetEasterEggCounter()
    }

    private fun configureClickableLinks() {
        with(binding.includeContent.aboutText) {
            text = addClickableLinks()
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun addClickableLinks(): SpannableString {
        val fullText = getText(
            R.string.aboutDescriptionNew,
        ) as SpannedString
        val spannableString = SpannableString(fullText)
        val annotations = fullText.getSpans(0, fullText.length, Annotation::class.java)

        annotations?.find { it.value == PRIVACY_PROTECTION_ANNOTATION }?.let {
            addSpannable(
                spannableString,
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        viewModel.onPrivacyProtectionsLinkClicked()
                    }
                },
                fullText,
                it,
            )
        }

        annotations?.find { it.value == LEARN_MORE_ANNOTATION }?.let {
            addSpannable(
                spannableString,
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        viewModel.onLearnMoreLinkClicked()
                    }
                },
                fullText,
                it,
            )
        }

        return spannableString
    }

    private fun addSpannable(
        spannableString: SpannableString,
        clickableSpan: ClickableSpan,
        fullText: SpannedString,
        it: Annotation,
    ) {
        spannableString.apply {
            setSpan(
                clickableSpan,
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
            is Command.LaunchBrowserWithLearnMoreUrl -> launchBrowserScreen()
            is Command.LaunchWebViewWithPrivacyPolicyUrl -> launchWebViewScreen()
            is Command.LaunchBrowserWithPrivacyProtectionsUrl -> launchPrivacyProtectionsScreen()
            is Command.LaunchFeedback -> launchFeedback()
            is Command.LaunchPproUnifiedFeedback -> launchPproUnifiedFeedback()
        }
    }

    private fun launchBrowserScreen() {
        startActivity(BrowserActivity.intent(this, Url.ABOUT, interstitialScreen = true))
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
    }
}
