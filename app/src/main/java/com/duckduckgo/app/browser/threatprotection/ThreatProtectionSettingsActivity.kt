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

package com.duckduckgo.app.browser.threatprotection

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.CompoundButton
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityThreatProtectionSettingsBinding
import com.duckduckgo.app.browser.threatprotection.ThreatProtectionSettingsViewModel.Command
import com.duckduckgo.app.browser.threatprotection.ThreatProtectionSettingsViewModel.Command.OpenScamProtectionLearnMore
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(ThreatProtectionSettingsNoParams::class)
class ThreatProtectionSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: ThreatProtectionSettingsViewModel by bindViewModel()
    private val binding: ActivityThreatProtectionSettingsBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val scamProtectionToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onScamProtectionSettingChanged(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    private fun configureUiEventHandlers() {
        with(binding) {
            scamBlockerToggle.setOnCheckedChangeListener(scamProtectionToggleListener)
            scamProtectionSettingInfo.setSpannable(R.string.scamProtectionSettingInfo) { viewModel.scamProtectionLearnMoreClicked() }
        }
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState?.let {
                    binding.scamBlockerToggle.quietlySetIsChecked(
                        newCheckedState = it.scamProtectionEnabled,
                        changeListener = scamProtectionToggleListener,
                    )
                    binding.smarterEncryptionItem.setStatus(it.smarterEncryptionEnabled)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            OpenScamProtectionLearnMore -> {
                globalActivityStarter.start(
                    this,
                    WebViewActivityWithParams(
                        url = SCAM_PROTECTION_LEARN_MORE_URL,
                        screenTitle = getString(R.string.maliciousSiteLearnMoreTitle),
                    ),
                )
            }
        }
    }

    private fun DaxTextView.setSpannable(
        @StringRes errorResource: Int,
        actionHandler: () -> Unit,
    ) {
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                actionHandler()
            }
        }
        val htmlContent = context.getString(errorResource).html(context)
        val spannableString = SpannableStringBuilder(htmlContent)
        val urlSpans = htmlContent.getSpans(0, htmlContent.length, URLSpan::class.java)
        urlSpans?.forEach {
            spannableString.apply {
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
        text = spannableString
        movementMethod = LinkMovementMethod.getInstance()
    }

    companion object {
        private const val SCAM_PROTECTION_LEARN_MORE_URL = "https://duckduckgo.com/duckduckgo-help-pages/privacy/scam-blocker"
    }
}
