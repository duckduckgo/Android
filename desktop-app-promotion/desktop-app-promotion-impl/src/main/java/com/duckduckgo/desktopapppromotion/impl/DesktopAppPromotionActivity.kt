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

package com.duckduckgo.desktopapppromotion.impl

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
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionParams
import com.duckduckgo.desktopapppromotion.impl.DesktopAppPromotionShareBroadcastReceiver.Companion.EXTRA_HANDLER_ID
import com.duckduckgo.desktopapppromotion.impl.DesktopAppPromotionViewModel.Command
import com.duckduckgo.desktopapppromotion.impl.databinding.ActivityDesktopAppPromotionBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.getActivityParams
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DesktopAppPromotionParams::class, screenName = "getDesktopBrowser")
class DesktopAppPromotionActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var promotionViewModelFactory: DesktopAppPromotionViewModel.Factory

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityDesktopAppPromotionBinding by viewBinding()

    private val params: DesktopAppPromotionParams by lazy {
        intent.getActivityParams(DesktopAppPromotionParams::class.java) ?: DesktopAppPromotionParams()
    }

    private val content: DesktopAppPromotionContent by lazy { params.resolveContent(this) }

    private val viewModel: DesktopAppPromotionViewModel by lazy {
        ViewModelProvider.create(
            store = viewModelStore,
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) =
                    promotionViewModelFactory.create(content, params.pixels, params.handlerId) as T
            },
            extras = this.defaultViewModelCreationExtras,
        )[DesktopAppPromotionViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.MISC)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        supportActionBar?.title = content.toolbarTitle

        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }

        setupObservers()
        setupBackNavigationHandler()
        setupClickListeners()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        // Content ends in fixed bottom buttons, so keep them clear of the nav bar in every mode
        // rather than drawing behind the gesture handle.
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentScrollView, drawBehindGestureNav = false)
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
    }

    private fun render(viewState: DesktopAppPromotionViewModel.ViewState) {
        with(viewState.content) {
            binding.titleText.text = title
            binding.bodyText.text = body
            binding.desktopBrowserIcon.setImageResource(illustration)
            binding.browserUrl.text = downloadUrlDisplay
            binding.shareDownloadLinkButton.text = shareButtonLabel
            binding.noThanksButton.text = dismissButtonLabel
            binding.noThanksButton.isVisible = showDismissButton
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.ShareLink -> launchShareSheet(command.shareText, command.chooserTitle)
            is Command.ShowCopiedNotification -> showCopiedNotification()
            is Command.Close -> finish()
        }
    }

    private fun launchShareSheet(
        shareText: String,
        chooserTitle: String,
    ) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_TITLE, chooserTitle)
        }

        try {
            startActivity(Intent.createChooser(shareIntent, chooserTitle, shareCompletionIntentSender()))
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "Activity not found for share: ${e.asLog()}" }
        }
    }

    /**
     * Only worth building when a caller registered a handler — without one there is nothing to
     * report a completed share to, and callers that never had this behaviour keep a plain chooser.
     */
    private fun shareCompletionIntentSender() = params.handlerId?.let { handlerId ->
        PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, DesktopAppPromotionShareBroadcastReceiver::class.java).putExtra(EXTRA_HANDLER_ID, handlerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ).intentSender
    }

    private fun setupClickListeners() {
        binding.shareDownloadLinkButton.setOnClickListener { viewModel.onShareClicked() }
        binding.noThanksButton.setOnClickListener { viewModel.onDismissClicked() }
        binding.browserUrl.setOnClickListener { viewModel.onLinkClicked() }
    }

    private fun setupBackNavigationHandler() {
        onBackPressedDispatcher.addCallback(this) {
            viewModel.onBackPressed()
        }
    }

    private fun showCopiedNotification() {
        Snackbar.make(binding.root, content.linkCopiedMessage, Snackbar.LENGTH_SHORT).show()
    }
}
