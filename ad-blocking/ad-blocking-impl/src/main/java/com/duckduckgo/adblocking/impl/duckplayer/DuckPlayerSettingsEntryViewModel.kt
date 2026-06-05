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

package com.duckduckgo.adblocking.impl.duckplayer

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.DuckPlayerState.DISABLED_WIH_HELP_LINK
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_PRESSED
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsEntryViewModel.Command.OpenSettings
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toBinaryString
import com.duckduckgo.di.scopes.ViewScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class DuckPlayerSettingsEntryViewModel @Inject constructor(
    private val duckPlayer: DuckPlayerInternal,
    private val statusChecker: AdBlockingStatusChecker,
    private val pixel: Pixel,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(val isVisible: Boolean = false)

    sealed class Command {
        data object OpenSettings : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val viewStateJob = ConflatedJob()

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        viewStateJob += combine(
            duckPlayer.observeDuckPlayerState(),
            statusChecker.isShownInSettingsFlow(),
        ) { duckPlayerState, adBlockingShownInSettings ->
            val isVisible = !adBlockingShownInSettings &&
                (duckPlayerState == ENABLED || duckPlayerState == DISABLED_WIH_HELP_LINK)
            _viewState.update { it.copy(isVisible = isVisible) }
        }
            .flowOn(dispatcherProvider.io())
            .launchIn(viewModelScope)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        viewStateJob.cancel()
    }

    fun onSettingClicked() {
        viewModelScope.launch {
            command.send(OpenSettings)
            val wasUsedBefore = duckPlayer.wasUsedBefore()
            pixel.fire(DUCK_PLAYER_SETTINGS_PRESSED, parameters = mapOf("was_used_before" to wasUsedBefore.toBinaryString()))
        }
    }
}
