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

package com.duckduckgo.app.browser.webview

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityWebViewDevSettingsBinding
import com.duckduckgo.app.browser.webview.WebViewDevSettingsViewModel.ViewState
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.playstore.PlayStoreAndroidUtils.Companion.PLAY_STORE_PACKAGE
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(WebViewDevSettingsScreen::class)
class WebViewDevSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var viewModel: WebViewDevSettingsViewModel

    @Inject
    lateinit var clipboardManager: ClipboardInteractor

    private val binding: ActivityWebViewDevSettingsBinding by viewBinding()

    private lateinit var webViewDebugLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        registerActivityResultLauncher()
        configureListeners()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState ->
                updateViews(viewState)
            }.launchIn(lifecycleScope)
    }

    private fun updateViews(viewState: ViewState) {
        binding.webViewPackage.setSecondaryText(viewState.webViewPackage)
        binding.webViewVersion.setSecondaryText(viewState.webViewVersion)
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    private fun configureListeners() {
        binding.webViewDevTools.setOnClickListener {
            launchExternalIntentForAction(Intent(WEBVIEW_DEV_TOOLS_INTENT_ACTION), getString(R.string.webview_dev_ui_not_available))
        }
        binding.webViewVersion.setClickListener {
            clipboardManager.copyToClipboard(viewModel.viewState().value.webViewVersion, false)
        }
        binding.webViewPackage.setClickListener {
            clipboardManager.copyToClipboard(viewModel.viewState().value.webViewPackage, false)
        }
        binding.webViewPlayStore.setClickListener {
            val intent = Intent(Intent.ACTION_VIEW).also {
                it.setData(PLAYSTORE_WEBVIEW_INTENT_URI.toUri())
                it.setPackage(PLAY_STORE_PACKAGE)
            }
            launchExternalIntentForAction(intent, getString(R.string.webview_play_store_unavailable))
        }
    }

    private fun registerActivityResultLauncher() {
        webViewDebugLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        }
    }

    private fun launchExternalIntentForAction(intent: Intent, errorIfCannotLaunch: String) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, errorIfCannotLaunch, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val PLAYSTORE_WEBVIEW_INTENT_URI = "market://details?id=com.google.android.webview"
        private const val WEBVIEW_DEV_TOOLS_INTENT_ACTION = "com.android.webview.SHOW_DEV_UI"
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}

data object WebViewDevSettingsScreen : GlobalActivityStarter.ActivityParams {
    private fun readResolve(): Any = WebViewDevSettingsScreen
}
