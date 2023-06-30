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

package com.duckduckgo.app.webtrackingprotection

import android.app.ActivityOptions
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityWebTrackingProtectionBinding
import com.duckduckgo.app.browser.webview.WebViewActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.extensions.html
import com.duckduckgo.app.globalprivacycontrol.ui.GlobalPrivacyControlActivity
import com.duckduckgo.app.privacy.ui.WhitelistActivity
import com.duckduckgo.app.webtrackingprotection.WebTrackingProtectionViewModel.Command
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(WebTrackingProtectionScreenNoParams::class)
class WebTrackingProtectionActivity : DuckDuckGoActivity() {

    private val viewModel: WebTrackingProtectionViewModel by bindViewModel()
    private val binding: ActivityWebTrackingProtectionBinding by viewBinding()

    private val clickableSpan = object : ClickableSpan() {
        override fun onClick(widget: View) {
            viewModel.onLearnMoreSelected()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        configureClickableLink()
        observeViewModel()
    }

    private fun configureUiEventHandlers() {
        binding.globalPrivacyControlSetting.setClickListener { viewModel.onGlobalPrivacyControlClicked() }
        binding.whitelist.setClickListener { viewModel.onManageWhitelistSelected() }
    }

    private fun configureClickableLink() {
        val htmlGPCText = getString(R.string.webTrackingProtectionDescription).html(this)
        val gpcSpannableString = SpannableStringBuilder(htmlGPCText)
        val urlSpans = htmlGPCText.getSpans(0, htmlGPCText.length, URLSpan::class.java)
        urlSpans?.forEach {
            gpcSpannableString.apply {
                setSpan(
                    clickableSpan,
                    gpcSpannableString.getSpanStart(it),
                    gpcSpannableString.getSpanEnd(it),
                    gpcSpannableString.getSpanFlags(it),
                )
                removeSpan(it)
                trim()
            }
        }
        binding.webTrackingProtectionDescription.apply {
            text = gpcSpannableString
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                setGlobalPrivacyControlSetting(viewState.globalPrivacyControlEnabled)
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun setGlobalPrivacyControlSetting(enabled: Boolean) {
        val stateText = if (enabled) {
            getString(R.string.enabled)
        } else {
            getString(R.string.disabled)
        }
        binding.globalPrivacyControlSetting.setSecondaryText(stateText)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is Command.LaunchLearnMoreWebPage -> launchLearnMoreWebPage(it.url)
            is Command.LaunchGlobalPrivacyControl -> launchGlobalPrivacyControl()
            is Command.LaunchWhitelist -> launchWhitelist()
        }
    }

    private fun launchLearnMoreWebPage(url: String) {
        startActivity(
            WebViewActivity.intent(
                this,
                url,
                getString(R.string.webTrackingProtectionTitle),
            ),
        )
    }

    private fun launchWhitelist() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(WhitelistActivity.intent(this), options)
    }

    private fun launchGlobalPrivacyControl() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(GlobalPrivacyControlActivity.intent(this), options)
    }
}
