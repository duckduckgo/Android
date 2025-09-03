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

package com.duckduckgo.daxprompts.impl.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Annotation
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.WindowManager
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.daxprompts.api.DaxPromptBrowserComparisonNoParams
import com.duckduckgo.daxprompts.impl.R
import com.duckduckgo.daxprompts.impl.databinding.ActivityDaxPromptBrowserComparisonBinding
import com.duckduckgo.daxprompts.impl.ui.DaxPromptBrowserComparisonViewModel.Command
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DaxPromptBrowserComparisonNoParams::class)
class DaxPromptBrowserComparisonActivity : DuckDuckGoActivity() {
    private val viewModel: DaxPromptBrowserComparisonViewModel by bindViewModel()
    private val binding: ActivityDaxPromptBrowserComparisonBinding by viewBinding()

    private val startBrowserComparisonChartActivityForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                logcat { "Received RESULT_OK from BrowserComparisonChart" }
                viewModel.onDefaultBrowserSet()
            } else {
                logcat { "Received non-OK result from BrowserComparisonChart" }
                viewModel.onDefaultBrowserNotSet()
            }
        }

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        if (isDarkThemeEnabled()) {
            renderDarkUi()
        } else {
            renderLightUi()
        }
        configureClickableLinks()
        setupListeners()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        applyFullScreenFlags()
        markAsShown()
    }

    private fun renderDarkUi() {
        binding.orangeShape.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.background_shape_dark))
        binding.daxPromptBrowserComparisonContainer.setBackgroundColor(getColor(R.color.daxPromptBackgroundDark))
        binding.daxPromptBrowserComparisonMessageContainer.background = ContextCompat.getDrawable(this, R.drawable.background_dax_message_dark)
        binding.daxPromptBrowserComparisonPrimaryButton.background = ContextCompat.getDrawable(this, R.drawable.background_button_dark_with_ripple)
        binding.daxPromptBrowserComparisonPrimaryButton.setTextColor(getColor(com.duckduckgo.mobile.android.R.color.black))
        binding.daxPromptBrowserComparisonChart.featureIcon1.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.ic_comparison_chart_search_dark,
            ),
        )
    }

    private fun renderLightUi() {
        binding.orangeShape.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.background_shape))
        binding.daxPromptBrowserComparisonContainer.setBackgroundColor(getColor(R.color.daxPromptBackground))
        binding.daxPromptBrowserComparisonMessageContainer.background = ContextCompat.getDrawable(this, R.drawable.background_dax_message)
        binding.daxPromptBrowserComparisonPrimaryButton.background = ContextCompat.getDrawable(this, R.drawable.background_button_with_ripple)
        binding.daxPromptBrowserComparisonPrimaryButton.setTextColor(getColor(com.duckduckgo.mobile.android.R.color.white))
        binding.daxPromptBrowserComparisonChart.featureIcon1.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_comparison_chart_search))
    }

    private fun configureClickableLinks() {
        with(binding.daxPromptBrowserComparisonMoreLink) {
            text = addClickableLinks()
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun addClickableLinks(): SpannableString {
        val fullText = getText(R.string.dax_prompt_browser_comparison_more_link) as SpannedString

        val spannableString = SpannableString(fullText)
        val annotations = fullText.getSpans(0, fullText.length, Annotation::class.java)

        annotations?.find { it.value == LINK_ANNOTATION }?.let {
            addSpannable(spannableString, fullText, it) {
                viewModel.onMoreLinkClicked()
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
                Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
            setSpan(
                ForegroundColorSpan(
                    getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorAccentBlue),
                ),
                fullText.getSpanStart(it),
                fullText.getSpanEnd(it),
                Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
        }
    }

    private fun setupListeners() {
        binding.daxPromptBrowserComparisonCloseButton.setOnClickListener {
            viewModel.onCloseButtonClicked()
        }
        binding.daxPromptBrowserComparisonPrimaryButton.setOnClickListener {
            viewModel.onPrimaryButtonClicked()
        }
    }

    private fun setupObservers() {
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.CloseScreen -> {
                if (command.defaultBrowserSet == null) {
                    setResult(RESULT_OK)
                } else {
                    val resultIntent = Intent().apply {
                        putExtra(DAX_PROMPT_BROWSER_COMPARISON_SET_DEFAULT_EXTRA, command.defaultBrowserSet)
                    }
                    setResult(RESULT_OK, resultIntent)
                }
                finish()
            }

            is Command.BrowserComparisonChart -> {
                startBrowserComparisonChartActivityForResult.launch(command.intent)
            }

            is Command.OpenDetailsPage -> {
                globalActivityStarter.start(
                    this,
                    WebViewActivityWithParams(
                        url = command.url,
                        screenTitle = "",
                    ),
                )
            }
        }
    }

    private fun applyFullScreenFlags() {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.BLACK
        }
        ViewCompat.requestApplyInsets(binding.daxPromptBrowserComparisonContainer)
    }

    private fun markAsShown() {
        viewModel.markBrowserComparisonPromptAsShown()
    }

    companion object {
        private const val LINK_ANNOTATION = "more_link"
        const val DAX_PROMPT_BROWSER_COMPARISON_SET_DEFAULT_EXTRA = "DAX_PROMPT_BROWSER_COMPARISON_SET_DEFAULT_EXTRA"
    }
}
