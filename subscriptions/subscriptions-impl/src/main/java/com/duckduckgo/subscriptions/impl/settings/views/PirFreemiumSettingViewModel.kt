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

package com.duckduckgo.subscriptions.impl.settings.views

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.pir.api.freemium.PirFreemium
import com.duckduckgo.pir.api.freemium.PirFreemiumEntryPoint
import com.duckduckgo.subscriptions.impl.pixels.PirFreemiumCtaState
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver")
@ContributesViewModel(ViewScope::class)
class PirFreemiumSettingViewModel @Inject constructor(
    private val pirFreemium: PirFreemium,
    private val pixelSender: SubscriptionPixelSender,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(val entryPoint: PirFreemiumEntryPoint = PirFreemiumEntryPoint.HIDDEN)

    sealed class Command {
        data object OpenPirDashboard : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    // Survives configuration change with the ViewModel, so rotation doesn't inflate the funnel denominator.
    private var impressionFired = false

    // Resolved on resume rather than create, so returning from a completed scan flips the call to action.
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        viewModelScope.launch {
            val entryPoint = pirFreemium.getSettingsEntryPoint()
            _viewState.update { it.copy(entryPoint = entryPoint) }

            if (entryPoint != PirFreemiumEntryPoint.HIDDEN && !impressionFired) {
                impressionFired = true
                pixelSender.reportAppSettingsPirFreemiumImpression()
            }
        }
    }

    fun onEntryPointClicked() {
        val entryPoint = _viewState.value.entryPoint
        val ctaState = when (entryPoint) {
            PirFreemiumEntryPoint.HIDDEN -> return
            PirFreemiumEntryPoint.START_FREE_SCAN -> PirFreemiumCtaState.START_FREE_SCAN
            PirFreemiumEntryPoint.VIEW_SCAN_RESULTS -> PirFreemiumCtaState.VIEW_SCAN_RESULTS
        }

        pixelSender.reportAppSettingsPirFreemiumClick(ctaState)
        viewModelScope.launch { command.send(Command.OpenPirDashboard) }
    }
}
