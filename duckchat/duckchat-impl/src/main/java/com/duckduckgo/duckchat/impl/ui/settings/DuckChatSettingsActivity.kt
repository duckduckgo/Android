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

package com.duckduckgo.duckchat.impl.ui.settings

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.browser.api.ui.BrowserScreens.FeedbackActivityWithEmptyParams
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ActivityDuckChatSettingsBinding
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnel
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_SETTINGS_DISPLAYED
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.ViewState
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.settings.api.SettingsWebViewScreenWithParams
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DuckChatSettingsNoParams::class, screenName = "duckai.settings")
class DuckChatSettingsActivity : DuckDuckGoActivity() {
    private val viewModel: DuckChatSettingsViewModel by bindViewModel()
    private val binding: ActivityDuckChatSettingsBinding by viewBinding()

    private val userEnabledDuckChatToggleListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.onDuckChatUserEnabledToggled(isChecked)
            updateWidgets()
        }

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var settingsPageFeature: SettingsPageFeature

    @Inject
    lateinit var inputScreenDiscoveryFunnel: InputScreenDiscoveryFunnel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setupToolbar(binding.includeToolbar.toolbar)

        observeViewModel()

        pixel.fire(DUCK_CHAT_SETTINGS_DISPLAYED)
        inputScreenDiscoveryFunnel.onDuckAiSettingsSeen()
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        binding.userEnabledDuckChatToggle.quietlySetIsChecked(viewState.isDuckChatUserEnabled, userEnabledDuckChatToggleListener)

        // align content with the main Duck.ai toggle's text
        val offset =
            resources.getDimensionPixelSize(CommonR.dimen.listItemImageContainerSize) +
                resources.getDimensionPixelSize(CommonR.dimen.keyline_4)
        val orientation = resources.configuration.orientation
        binding.duckAiInputScreenToggleContainer.updatePadding(
            left =
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    0
                } else {
                    offset
                },
        )
        binding.duckAiInputScreenDescription.updatePadding(left = offset)
        binding.duckAiShortcuts.updatePadding(left = offset)

        binding.duckChatSettingsText.addClickableSpan(
            textSequence = getText(R.string.duck_chat_settings_activity_description),
            spans =
            listOf(
                "learn_more_link" to
                    object : DuckDuckGoClickableSpan() {
                        override fun onClick(widget: View) {
                            viewModel.duckChatLearnMoreClicked()
                        }
                    },
            ),
        )

        binding.duckAiInputScreenToggleContainer.isVisible = viewState.shouldShowInputScreenToggle
        configureInputScreenToggle(
            withoutAi = InputScreenToggleButton.WithoutAi(isActive = !viewState.isInputScreenEnabled, appTheme.isLightModeEnabled()),
            withAi = InputScreenToggleButton.WithAi(isActive = viewState.isInputScreenEnabled, appTheme.isLightModeEnabled()),
        )

        binding.duckAiInputScreenDescription.isVisible = viewState.shouldShowInputScreenToggle
        binding.duckAiInputScreenDescription.addClickableSpan(
            textSequence = getText(R.string.input_screen_user_pref_description),
            spans =
            listOf(
                "share_feedback" to
                    object : DuckDuckGoClickableSpan() {
                        override fun onClick(widget: View) {
                            viewModel.duckAiInputScreenShareFeedbackClicked()
                        }
                    },
            ),
        )

        binding.duckAiShortcuts.isVisible = viewState.shouldShowShortcuts
        binding.duckAiShortcuts.setOnClickListener {
            viewModel.onDuckAiShortcutsClicked()
        }

        binding.showDuckChatSearchSettingsLink.setOnClickListener {
            viewModel.duckChatSearchAISettingsClicked()
        }
        binding.duckAiInputScreenWithoutAiContainer.setOnClickListener {
            viewModel.onDuckAiInputScreenWithoutAiSelected()
        }
        binding.duckAiInputScreenWithAiContainer.setOnClickListener {
            viewModel.onDuckAiInputScreenWithAiSelected()
        }
    }

    private fun processCommand(command: DuckChatSettingsViewModel.Command) {
        when (command) {
            is DuckChatSettingsViewModel.Command.OpenLink -> {
                if (settingsPageFeature.embeddedSettingsWebView().isEnabled()) {
                    globalActivityStarter.start(
                        this,
                        SettingsWebViewScreenWithParams(
                            url = command.link,
                            screenTitle = getString(command.titleRes),
                        ),
                    )
                } else {
                    globalActivityStarter.start(
                        this,
                        WebViewActivityWithParams(
                            url = command.link,
                            screenTitle = getString(R.string.duck_chat_title),
                        ),
                    )
                }
            }
            is DuckChatSettingsViewModel.Command.OpenLinkInNewTab -> {
                startActivity(browserNav.openInNewTab(this@DuckChatSettingsActivity, command.link))
            }

            is DuckChatSettingsViewModel.Command.OpenShortcutSettings -> {
                val intent = Intent(this, DuckAiShortcutSettingsActivity::class.java)
                startActivity(intent)
            }

            is DuckChatSettingsViewModel.Command.LaunchFeedback -> {
                globalActivityStarter.start(this, FeedbackActivityWithEmptyParams)
            }
        }
    }

    private fun configureInputScreenToggle(
        withoutAi: InputScreenToggleButton,
        withAi: InputScreenToggleButton,
    ) = with(binding) {
        val context = this@DuckChatSettingsActivity
        duckAiInputScreenToggleWithoutAiImage.setImageDrawable(ContextCompat.getDrawable(context, withoutAi.imageRes))
        duckAiInputScreenToggleWithoutAiCheck.setImageDrawable(ContextCompat.getDrawable(context, withoutAi.checkRes))

        duckAiInputScreenToggleWithAiImage.setImageDrawable(ContextCompat.getDrawable(context, withAi.imageRes))
        duckAiInputScreenToggleWithAiCheck.setImageDrawable(ContextCompat.getDrawable(context, withAi.checkRes))
    }

    private sealed class InputScreenToggleButton(
        isActive: Boolean,
    ) {
        abstract val imageRes: Int

        val checkRes: Int =
            if (isActive) {
                CommonR.drawable.ic_check_accent_24
            } else {
                CommonR.drawable.ic_shape_circle_disabled_24
            }

        class WithoutAi(
            isActive: Boolean,
            isLightMode: Boolean,
        ) : InputScreenToggleButton(isActive) {
            override val imageRes: Int =
                when {
                    isActive && isLightMode -> R.drawable.searchbox_withoutai_active
                    isActive && !isLightMode -> R.drawable.searchbox_withoutai_active_dark
                    !isActive && isLightMode -> R.drawable.searchbox_withoutai_inactive
                    else -> R.drawable.searchbox_withoutai_inactive_dark
                }
        }

        class WithAi(
            isActive: Boolean,
            isLightMode: Boolean,
        ) : InputScreenToggleButton(isActive) {
            override val imageRes: Int =
                when {
                    isActive && isLightMode -> R.drawable.searchbox_withai_active
                    isActive && !isLightMode -> R.drawable.searchbox_withai_active_dark
                    !isActive && isLightMode -> R.drawable.searchbox_withai_inactive
                    else -> R.drawable.searchbox_withai_inactive_dark
                }
        }
    }

    private fun updateWidgets() {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        sendBroadcast(intent)
    }
}
