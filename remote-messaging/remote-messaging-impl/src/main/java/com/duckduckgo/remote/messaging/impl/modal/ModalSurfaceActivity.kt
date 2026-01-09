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

package com.duckduckgo.remote.messaging.impl.modal

import android.os.Bundle
import androidx.activity.addCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.remote.messaging.impl.databinding.ActivityModalSurfaceBinding
import com.duckduckgo.remote.messaging.impl.modal.ModalSurfaceViewModel.Command
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageView
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(ModalSurfaceActivityFromMessageId::class)
class ModalSurfaceActivity : DuckDuckGoActivity(), CardsListRemoteMessageView.CardsListRemoteMessageListener {

    private val viewModel: ModalSurfaceViewModel by bindViewModel()
    private val binding: ActivityModalSurfaceBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initialise()
        setupObservers()
        setupBackNavigationHandler()
    }

    override fun onDismiss() {
        viewModel.onDismiss()
    }

    private fun initialise() {
        viewModel.onInitialise(intent.getActivityParams(ModalSurfaceActivityFromMessageId::class.java))
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

    private fun render(viewState: ModalSurfaceViewModel.ViewState?) {
        if (viewState == null) return

        if (viewState.showCardsListView) {
            binding.cardsListRemoteMessageView.messageId = viewState.messageId
            binding.cardsListRemoteMessageView.listener = this
            binding.cardsListRemoteMessageView.show()
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.DismissMessage -> {
                finish()
            }
        }
    }

    private fun setupBackNavigationHandler() {
        onBackPressedDispatcher.addCallback(this) {
            viewModel.onBackPressed()
        }
    }
}
