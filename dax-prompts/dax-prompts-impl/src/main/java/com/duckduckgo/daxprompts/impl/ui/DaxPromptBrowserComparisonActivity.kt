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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.daxprompts.api.DaxPromptBrowserComparisonParams
import com.duckduckgo.daxprompts.api.LaunchSource
import com.duckduckgo.daxprompts.impl.R
import com.duckduckgo.daxprompts.impl.databinding.ActivityDaxPromptBrowserComparisonBinding
import com.duckduckgo.daxprompts.impl.ui.DaxPromptBrowserComparisonViewModel.Command
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DaxPromptBrowserComparisonParams::class)
class DaxPromptBrowserComparisonActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var daxPromptBrowserComparisonViewModelFactory: DaxPromptBrowserComparisonViewModel.Factory

    private val viewModel: DaxPromptBrowserComparisonViewModel by lazy {
        val launchSource = intent.getActivityParams(DaxPromptBrowserComparisonParams::class.java)?.launchSource
            ?: LaunchSource.REACTIVATE_USERS
        getDaxPromptBrowserComparisonViewModel(launchSource)
    }

    private val binding: ActivityDaxPromptBrowserComparisonBinding by viewBinding()

    private var lockedInPortraitMode: Boolean = false

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

    private val startSystemDefaultAppsSettingsForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            logcat { "Returned from system default apps settings" }
            viewModel.onSystemDefaultAppsSettingsReturned()
        }

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setupListeners()
        setupObservers()
        setupOnBackNavigation()
        setupOrientationMode()

        viewModel.onPromptShown()
    }

    override fun onResume() {
        super.onResume()
        applyFullScreenFlags()
        markAsShown()
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (lockedInPortraitMode && newConfig.orientation != Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        }
    }

    private fun setupListeners() {
        binding.daxPromptBrowserComparisonCloseButton.setOnClickListener {
            viewModel.onCloseButtonClicked()
        }
        binding.daxPromptBrowserComparisonPrimaryButton.setOnClickListener {
            viewModel.onPrimaryButtonClicked()
        }

        binding.daxPromptBrowserComparisonGhostButton.setOnClickListener {
            viewModel.onGhostButtonClicked()
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

            is Command.LaunchSystemDefaultAppsSettings -> {
                startSystemDefaultAppsSettingsForResult.launch(systemDefaultAppsSettingsIntent())
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

    private fun setupOnBackNavigation() { // Added method
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.onBackNavigation()
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            },
        )
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

    private fun getDaxPromptBrowserComparisonViewModel(launchSource: LaunchSource): DaxPromptBrowserComparisonViewModel = ViewModelProvider(
        owner = this,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                daxPromptBrowserComparisonViewModelFactory.create(launchSource) as T
        },
    )[DaxPromptBrowserComparisonViewModel::class.java]

    private fun systemDefaultAppsSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
            putExtra(SETTINGS_SELECT_OPTION_KEY, DEFAULT_BROWSER_APP_OPTION)
            putExtra(SETTINGS_SHOW_FRAGMENT_ARGS, bundleOf(SETTINGS_SELECT_OPTION_KEY to DEFAULT_BROWSER_APP_OPTION))
        }

    companion object {
        const val DAX_PROMPT_BROWSER_COMPARISON_SET_DEFAULT_EXTRA = "DAX_PROMPT_BROWSER_COMPARISON_SET_DEFAULT_EXTRA"

        private const val SETTINGS_SELECT_OPTION_KEY = ":settings:fragment_args_key"
        private const val SETTINGS_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
        private const val DEFAULT_BROWSER_APP_OPTION = "default_browser"
    }
}
