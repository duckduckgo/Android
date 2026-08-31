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
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autoconsent.impl.R
import com.duckduckgo.autoconsent.impl.databinding.ActivityCookiePopupOptInBinding
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.Command
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.Variant
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.ViewState
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

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
            edgeToEdgeHandler.applySystemBarInsets(binding.cookiePopupOptInContainer)
        }

        if (SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.slide_to_bottom)
        }

        setupTextAppearances()
        setupListeners()
        setupObservers()
        setupOrientationMode()
        setupOnBackNavigation()

        // A restored Activity is the same presentation, so only a fresh one counts against the display cap.
        if (savedInstanceState == null) {
            viewModel.onPromptShown()
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (lockedInPortraitMode && newConfig.orientation != Configuration.ORIENTATION_PORTRAIT) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun finish() {
        super.finish()
        if (SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, R.anim.slide_to_bottom)
        }
    }

    private fun setupTextAppearances() {
        binding.cookiePopupOptInBrand.setTextAppearance(R.style.Typography_DuckDuckGo_CookiePopupOptIn_Brand)
        binding.cookiePopupOptInFootnote.setTextAppearance(R.style.Typography_DuckDuckGo_CookiePopupOptIn_Hint)
    }

    /**
     * The prompt is a required choice unless explicitly made dismissible through remote config.
     */
    private fun setupOnBackNavigation() {
        if (viewModel.viewState.value.isBackNavigationEnabled) return

        onBackPressedDispatcher.addCallback(
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )
    }

    private fun setupListeners() {
        binding.cookiePopupOptInCloseButton.setOnClickListener {
            viewModel.onCloseClicked()
        }
        binding.cookiePopupOptInAcceptButton.setOnClickListener {
            disableButtons()
            viewModel.onAcceptClicked()
        }
        binding.cookiePopupOptInDeclineButton.setOnClickListener {
            disableButtons()
            viewModel.onDeclineClicked()
        }
    }

    private fun disableButtons() {
        binding.cookiePopupOptInAcceptButton.isEnabled = false
        binding.cookiePopupOptInDeclineButton.isEnabled = false
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
        binding.cookiePopupOptInCloseButton.isVisible = viewState.isCloseButtonVisible
        binding.cookiePopupOptInTitle.setText(text.title)
        binding.cookiePopupOptInDescription.setText(text.description)
        binding.cookiePopupOptInAcceptButton.text = getString(text.acceptButton)
        binding.cookiePopupOptInDeclineButton.text = getString(text.declineButton)
    }

    private fun Variant.textResources(): TextResources = when (this) {
        Variant.PROTECTION_ON -> TextResources(
            title = R.string.autoconsentPromptTitle,
            description = R.string.autoconsentPromptCpmOnMessage,
            acceptButton = R.string.autoconsentPromptCpmOnPrimaryButton,
            declineButton = R.string.autoconsentPromptCpmOnSecondaryButton,
        )

        Variant.PROTECTION_OFF -> TextResources(
            title = R.string.autoconsentPromptTitle,
            description = R.string.autoconsentPromptCpmOffMessage,
            acceptButton = R.string.autoconsentPromptCpmOffPrimaryButton,
            declineButton = R.string.autoconsentPromptCpmOffSecondaryButton,
        )
    }

    private data class TextResources(
        @field:StringRes val title: Int,
        @field:StringRes val description: Int,
        @field:StringRes val acceptButton: Int,
        @field:StringRes val declineButton: Int,
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
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, CookiePopupOptInActivity::class.java)
    }
}
