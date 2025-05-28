/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.macos.impl

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.macos.api.MacOsScreenWithEmptyParams
import com.duckduckgo.macos.impl.MacOsViewModel.Command
import com.duckduckgo.macos.impl.MacOsViewModel.Command.GoToWindowsClientSettings
import com.duckduckgo.macos.impl.MacOsViewModel.Command.ShareLink
import com.duckduckgo.macos.impl.R.string
import com.duckduckgo.macos.impl.databinding.ActivityMacosBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.windows.api.ui.WindowsScreenWithEmptyParams
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(MacOsScreenWithEmptyParams::class)
class MacOsActivity : DuckDuckGoActivity() {

    private val viewModel: MacOsViewModel by bindViewModel()
    private val binding: ActivityMacosBinding by viewBinding()

    @Inject lateinit var globalActivityStarter: GlobalActivityStarter

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { executeCommand(it) }
            .launchIn(lifecycleScope)

        setContentView(binding.root)
        setupToolbar(toolbar)
        configureUiEventHandlers()
    }

    private fun configureUiEventHandlers() {
        binding.shareButton.setOnClickListener {
            viewModel.onShareClicked()
        }

        binding.lookingForWindowsVersionButton.setOnClickListener {
            viewModel.onGoToWindowsClicked()
        }
    }

    private fun executeCommand(command: Command) {
        when (command) {
            is ShareLink -> launchSharePageChooser(command.originEnabled)
            is GoToWindowsClientSettings -> launchWindowsClientSettings()
        }
    }

    private fun launchWindowsClientSettings() {
        globalActivityStarter.start(this, WindowsScreenWithEmptyParams)
        finish()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun launchSharePageChooser(addOrigin: Boolean) {
        var shareText = getString(string.macos_share_text)
        if (!addOrigin) { shareText = shareText.replace(ORIGIN_URL_PATH, "") }
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_TITLE, getString(string.macos_share_title))
        }

        val pi = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, MacOsLinkShareBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            startActivity(Intent.createChooser(share, getString(string.macos_share_title), pi.intentSender))
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "Activity not found: ${e.asLog()}" }
        }
    }

    companion object {
        const val ORIGIN_URL_PATH = "?origin=funnel_browser_android_settings"
    }
}
