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

package com.duckduckgo.duckchat.impl.subscription

import android.app.ActivityOptions
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.duckchat.impl.R.string
import com.duckduckgo.duckchat.impl.databinding.ActivityRevengeAiPaidSettingsBinding
import com.duckduckgo.duckchat.impl.subscription.RevengeAIPaidSettingsViewModel.Command
import com.duckduckgo.duckchat.impl.subscription.RevengeAIPaidSettingsViewModel.Command.LaunchLearnMoreWebPage
import com.duckduckgo.duckchat.impl.subscription.RevengeAIPaidSettingsViewModel.Command.OpenRevengeAI
import com.duckduckgo.duckchat.impl.subscription.RevengeAIPaidSettingsViewModel.Command.OpenDuckChatSettings
import com.duckduckgo.duckchat.impl.subscription.RevengeAIPaidSettingsViewModel.ViewState
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.duckchat.impl.R as DuckChatR
import com.duckduckgo.mobile.android.R as CommonR

object RevengeAIPaidSettingsNoParams : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(RevengeAIPaidSettingsNoParams::class)
class RevengeAIPaidSettingsActivity : DuckDuckGoActivity() {

    @Inject lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject lateinit var duckChat: DuckChat

    private val viewModel: RevengeAIPaidSettingsViewModel by bindViewModel()
    private val binding: ActivityRevengeAiPaidSettingsBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val clickableSpan = object : ClickableSpan() {
        override fun onClick(widget: View) {
            viewModel.onLearnMoreSelected()
        }

        override fun updateDrawState(ds: TextPaint) {
            super.updateDrawState(ds)
            ds.color = getColorFromAttr(CommonR.attr.daxColorAccentBlue)
            ds.isUnderlineText = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(toolbar)

        configureUiEventHandlers()
        configureClickableLink()
        observeViewModel()
    }

    private fun configureUiEventHandlers() {
        binding.duckAiPaidSettingsOpenRevengeAI.setOnClickListener {
            viewModel.onOpenRevengeAISelected()
        }
        binding.duckAiPaidSettingsEnableInSettings.setOnClickListener {
            viewModel.onEnableInSettingsSelected()
        }
    }

    private fun observeViewModel() {
        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { viewState -> viewState?.let { renderViewState(it) } }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        with(binding) {
            if (viewState.isRevengeAIPaidSettingsFeatureEnabled) {
                duckAiPaidSettingsIcon.setImageResource(DuckChatR.drawable.duckai_128)
            } else {
                duckAiPaidSettingsIcon.setImageResource(DuckChatR.drawable.duckai_ddg_128)
            }

            statusIndicator.setStatus(viewState.isDuckAIEnabled)
            duckAiPaidSettingsOpenRevengeAI.isVisible = viewState.isDuckAIEnabled
            duckAiPaidSettingsEnableInSettings.isVisible = true
            duckAiPaidSettingsEnableInSettings.setPrimaryText(
                if (viewState.isDuckAIEnabled) {
                    getString(string.duck_ai_paid_settings_manage_in_settings)
                } else {
                    getString(string.duck_ai_paid_settings_enable_in_settings)
                },
            )
            duckAiPaidSettingsEnableInSettings.setSecondaryText(
                if (viewState.isDuckAIEnabled) {
                    getString(string.duck_ai_paid_settings_manage_secondary)
                } else {
                    ""
                },
            )
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is LaunchLearnMoreWebPage -> {
                val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
                globalActivityStarter.start(this, WebViewActivityWithParams(command.url, getString(command.titleId)), options)
            }

            OpenRevengeAI -> {
                duckChat.openDuckChat()
            }

            OpenDuckChatSettings -> {
                globalActivityStarter.start(this, DuckChatSettingsNoParams)
            }
        }
    }

    private fun configureClickableLink() {
        val htmlText = getString(
            string.duck_ai_paid_settings_page_description,
        ).html(this)
        val spannableString = SpannableStringBuilder(htmlText)
        val urlSpans = htmlText.getSpans(0, htmlText.length, URLSpan::class.java)
        urlSpans?.forEach {
            spannableString.apply {
                insert(spannableString.getSpanStart(it), "\n")
                setSpan(
                    clickableSpan,
                    spannableString.getSpanStart(it),
                    spannableString.getSpanEnd(it),
                    spannableString.getSpanFlags(it),
                )
                removeSpan(it)
                trim()
            }
        }
        binding.duckAiPaidSettingsDescription.apply {
            text = spannableString
            movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
