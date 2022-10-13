/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.macos_impl

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.macos_impl.MacOsViewModel.Command
import com.duckduckgo.macos_impl.MacOsViewModel.Command.ShareLink
import com.duckduckgo.macos_impl.databinding.ActivityMacosBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@InjectWith(ActivityScope::class)
class MacOsActivity : DuckDuckGoActivity() {

    private val viewModel: MacOsViewModel by bindViewModel()
    private val binding: ActivityMacosBinding by viewBinding()

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
    }

    private fun executeCommand(command: Command) {
        when (command) {
            is ShareLink -> launchSharePageChooser()
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun launchSharePageChooser() {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.macos_share_text))
            putExtra(Intent.EXTRA_TITLE, getString(R.string.macos_share_title))
        }

        val pi = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, MacOsLinkShareBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            startActivity(Intent.createChooser(share, getString(R.string.macos_share_title), pi.intentSender))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Activity not found")
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, MacOsActivity::class.java)
        }
    }
}
