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

package com.duckduckgo.pir.impl.freemium.settings

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.pir.impl.freemium.PirFreemium
import com.duckduckgo.pir.impl.freemium.PirFreemiumState
import com.duckduckgo.pir.impl.pixels.PirFreemiumCtaState
import com.duckduckgo.pir.impl.pixels.PirPixelSender
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
    private val pixelSender: PirPixelSender,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(val freemiumState: PirFreemiumState = PirFreemiumState.NOT_ELIGIBLE)

    sealed class Command {
        data object OpenPirDashboard : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private var impressionFired = false

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        viewModelScope.launch {
            val freemiumState = pirFreemium.getPirFreemiumState()
            _viewState.update { it.copy(freemiumState = freemiumState) }

            if (freemiumState != PirFreemiumState.NOT_ELIGIBLE && !impressionFired) {
                impressionFired = true
                pixelSender.reportFreemiumSettingsEntryPointImpression()
            }
        }
    }

    fun onEntryPointClicked() {
        val ctaState = when (_viewState.value.freemiumState) {
            PirFreemiumState.NOT_ELIGIBLE -> return
            PirFreemiumState.ELIGIBLE -> PirFreemiumCtaState.START_FREE_SCAN
            PirFreemiumState.USED -> PirFreemiumCtaState.VIEW_SCAN_RESULTS
        }

        pixelSender.reportFreemiumSettingsEntryPointClicked(ctaState)
        viewModelScope.launch { command.send(Command.OpenPirDashboard) }
    }
}
