/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.prompt

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autoconsent.impl.R
import com.duckduckgo.autoconsent.impl.databinding.ActivityCookiePopupOptInBinding
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.Choice
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.Command
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.Variant
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.ViewState
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class CookiePopupOptInActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityCookiePopupOptInBinding by viewBinding()

    private val viewModel: CookiePopupOptInViewModel by bindViewModel()

    private var lockedInPortraitMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.ONBOARDING)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }

        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        setContentView(binding.root)
        if (edgeToEdgeEnabled) {
            edgeToEdgeHandler.applyStatusBarInsets(binding.cookiePopupOptInContainer, installScrim = false)
        }

        if (SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.slide_to_bottom)
        }

        setupOptions()
        setupListeners()
        setupObservers()
        setupOrientationMode()
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (lockedInPortraitMode && newConfig.orientation != Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        }
    }

    override fun finish() {
        super.finish()
        if (SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, R.anim.slide_to_bottom)
        }
    }

    private fun setupOptions() {
        binding.cookiePopupOptInSettingsHint.text = getString(R.string.autoconsentPopupSettingsHint).html(this)

        binding.cookiePopupOptInMaxOption.optionText.setTextAppearance(CommonR.style.Typography_DuckDuckGo_Onboarding_Body_InContext)
        binding.cookiePopupOptInKeepCurrentOption.optionText.setTextAppearance(CommonR.style.Typography_DuckDuckGo_Onboarding_Body_InContext)
        binding.cookiePopupOptInSettingsHint.setTextAppearance(R.style.Typography_DuckDuckGo_CookiePopupOptIn_Hint)
    }

    private fun setupListeners() {
        binding.cookiePopupOptInMaxOption.root.setOnClickListener {
            viewModel.onOptionSelected(Choice.MAX)
        }
        binding.cookiePopupOptInKeepCurrentOption.root.setOnClickListener {
            viewModel.onOptionSelected(Choice.KEEP_CURRENT)
        }
        binding.cookiePopupOptInConfirmButton.setOnClickListener {
            viewModel.onConfirmClicked()
        }
    }

    private fun setupObservers() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun render(viewState: ViewState) {
        val text = viewState.variant.textResources()
        binding.cookiePopupOptInTitle.setText(text.title)
        binding.cookiePopupOptInDescription.setText(text.description)
        binding.cookiePopupOptInMaxOption.optionText.setText(text.maxOption)
        binding.cookiePopupOptInKeepCurrentOption.optionText.setText(text.keepCurrentOption)

        val maxSelected = viewState.selected == Choice.MAX
        binding.cookiePopupOptInMaxOption.root.isSelected = maxSelected
        binding.cookiePopupOptInKeepCurrentOption.root.isSelected = !maxSelected
    }

    private fun Variant.textResources(): TextResources = when (this) {
        Variant.PROTECTION_ON -> TextResources(
            title = R.string.autoconsentPopupTitleWithFeatureEnabled,
            description = R.string.autoconsentPopupDescriptionWithFeatureEnabled,
            maxOption = R.string.autoconsentPopupDefaultOptionWithFeatureEnabled,
            keepCurrentOption = R.string.autoconsentPopupRejectOptionWithFeatureEnabled,
        )

        Variant.PROTECTION_OFF -> TextResources(
            title = R.string.autoconsentPopupTitleWithFeatureDisabled,
            description = R.string.autoconsentPopupDescriptionWithFeatureDisabled,
            maxOption = R.string.autoconsentPopupDefaultOptionWithFeatureDisabled,
            keepCurrentOption = R.string.autoconsentPopupRejectOptionWithFeatureDisabled,
        )
    }

    private data class TextResources(
        @StringRes val title: Int,
        @StringRes val description: Int,
        @StringRes val maxOption: Int,
        @StringRes val keepCurrentOption: Int,
    )

    private fun processCommand(command: Command) {
        when (command) {
            is Command.Close -> finish()
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientationMode() {
        lockedInPortraitMode = resources.getBoolean(R.bool.lockedInPortraitMode)
        if (lockedInPortraitMode) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, CookiePopupOptInActivity::class.java)
    }
}
