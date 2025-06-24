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

package com.duckduckgo.sync.impl.promotion

import android.annotation.SuppressLint
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
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivitySyncGetOnOtherDevicesBinding
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsViewModel.Command
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsViewModel.Command.ShareLink
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsViewModel.Command.ShowCopiedNotification
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SyncGetOnOtherPlatformsParams::class)
class SyncGetOnOtherPlatformsActivity : DuckDuckGoActivity() {

    private val viewModel: SyncGetOnOtherPlatformsViewModel by bindViewModel()
    private val binding: ActivitySyncGetOnOtherDevicesBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { executeCommand(it) }
            .launchIn(lifecycleScope)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        configureUiEventHandlers()
        if (savedInstanceState == null) {
            viewModel.onScreenShownToUser(extractLaunchSource())
        }
    }

    private fun configureUiEventHandlers() {
        binding.shareLinkButton.setOnClickListener {
            viewModel.onShareClicked(extractLaunchSource())
        }
        binding.downloadLinkText.setOnClickListener {
            viewModel.onLinkClicked(extractLaunchSource())
        }
    }

    private fun executeCommand(command: Command) {
        when (command) {
            is ShareLink -> launchSharePageChooser(command.link)
            is ShowCopiedNotification -> showCopiedNotification()
        }
    }

    private fun showCopiedNotification() {
        Snackbar.make(binding.root, R.string.syncGetAppsOnOtherPlatformInstructionUrlLinkCopied, Snackbar.LENGTH_SHORT).show()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun launchSharePageChooser(link: String) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, link)
            putExtra(Intent.EXTRA_TITLE, getString(R.string.syncGetAppsOnOtherPlatforms))
        }

        try {
            startActivity(Intent.createChooser(share, null))
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "Activity not found: ${e.asLog()}" }
        }
    }

    private fun extractLaunchSource(): String? {
        return intent.getActivityParams(SyncGetOnOtherPlatformsParams::class.java)?.source?.value
    }
}

data class SyncGetOnOtherPlatformsParams(val source: SyncGetOnOtherPlatformsLaunchSource) : GlobalActivityStarter.ActivityParams

enum class SyncGetOnOtherPlatformsLaunchSource(val value: String) {
    SOURCE_ACTIVATING("activating"),
    SOURCE_SYNC_DISABLED("not_activated"),
    SOURCE_SYNC_ENABLED("activated"),
}
