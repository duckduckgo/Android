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

package com.duckduckgo.app.desktopbrowser

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityGetDesktopBrowserBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.navigation.api.getActivityParams
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(GetDesktopBrowserActivityParams::class, screenName = "getDesktopBrowser")
class GetDesktopBrowserActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var getDesktopBrowserViewModelFactory: GetDesktopBrowserViewModel.Factory

    @Inject
    lateinit var shareEventHandler: GetDesktopBrowserShareEventHandler

    private val binding: ActivityGetDesktopBrowserBinding by viewBinding()

    private val viewModel by lazy {
        val params = intent.getActivityParams(GetDesktopBrowserActivityParams::class.java)
            ?: throw IllegalArgumentException("GetDesktopBrowserActivityParams are required")
        getDesktopBrowserViewModel(params)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupObservers()
        setupBackNavigationHandler()
        setupClickListeners()
        setupToolbar(binding.includeToolbar.toolbar)
    }

    private fun setupObservers() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        // Observe share events and close activity when link is shared
        shareEventHandler.linkShared
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { shared ->
                if (shared) {
                    shareEventHandler.consumeEvent()
                    setResult(RESULT_DISMISSED_OR_SHARED)
                    finish()
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun render(viewState: GetDesktopBrowserViewModel.ViewState) {
        binding.noThanksButton.isVisible = viewState.showNoThanksButton
    }

    private fun processCommand(command: GetDesktopBrowserViewModel.Command) {
        when (command) {
            is GetDesktopBrowserViewModel.Command.Close -> {
                finish()
            }

            is GetDesktopBrowserViewModel.Command.Dismissed -> {
                setResult(RESULT_DISMISSED_OR_SHARED)
                finish()
            }

            is GetDesktopBrowserViewModel.Command.ShareDownloadLink -> {
                launchShareSheet(command.url)
            }

            GetDesktopBrowserViewModel.Command.ShowCopiedNotification -> {
                showCopiedNotification()
            }
        }
    }

    private fun launchShareSheet(shareLink: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareLink)
            putExtra(Intent.EXTRA_TITLE, getString(R.string.getDesktopBrowserShareDownloadLink))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, GetDesktopBrowserShareBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            startActivity(
                Intent.createChooser(
                    shareIntent,
                    getString(R.string.getDesktopBrowserShareDownloadLink),
                    pendingIntent.intentSender,
                ),
            )
        } catch (e: ActivityNotFoundException) {
            logcat(LogPriority.WARN) { "Activity not found for share: $e" }
        }
    }

    private fun setupClickListeners() {
        binding.shareDownloadLinkButton.setOnClickListener {
            viewModel.onShareDownloadLinkClicked()
        }
        binding.noThanksButton.setOnClickListener {
            viewModel.onNoThanksClicked()
        }
        binding.browserUrl.setOnClickListener {
            viewModel.onLinkClicked()
        }
    }

    private fun setupBackNavigationHandler() {
        onBackPressedDispatcher.addCallback(this) {
            viewModel.onBackPressed()
        }
    }

    private fun showCopiedNotification() {
        Snackbar.make(binding.root, R.string.getDesktopBrowserUrlLinkCopied, Snackbar.LENGTH_SHORT).show()
    }

    private fun getDesktopBrowserViewModel(params: GetDesktopBrowserActivityParams): GetDesktopBrowserViewModel = ViewModelProvider.create(
        store = viewModelStore,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = getDesktopBrowserViewModelFactory.create(params) as T
        },
        extras = this.defaultViewModelCreationExtras,
    )[GetDesktopBrowserViewModel::class.java]

    companion object {
        const val RESULT_DISMISSED_OR_SHARED = 100
    }
}

data class GetDesktopBrowserActivityParams(
    val source: Source,
) : ActivityParams {
    enum class Source {
        COMPLETE_SETUP,
        OTHER,
    }
}
