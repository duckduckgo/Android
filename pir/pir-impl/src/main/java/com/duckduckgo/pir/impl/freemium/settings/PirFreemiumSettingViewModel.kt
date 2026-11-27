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
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint
import com.duckduckgo.pir.impl.freemium.PirFreemium
import com.duckduckgo.pir.impl.freemium.PirFreemiumState
import com.duckduckgo.pir.impl.pixels.PirFreemiumCtaState
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver")
@ContributesViewModel(ViewScope::class)
class PirFreemiumSettingViewModel @Inject constructor(
    private val pirFreemium: PirFreemium,
    private val pixelSender: PirPixelSender,
    private val pirFreemiumDataStore: PirFreemiumDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    /** [cta] is null when the entry point must be hidden. */
    data class ViewState(val cta: PirFreemiumCtaState? = null)

    sealed class Command {
        data class OpenPirDashboard(val entryPoint: PirFreeScanEntryPoint) : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private var lastImpressionCta: PirFreemiumCtaState? = null

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        viewModelScope.launch {
            val cta = resolveCta(pirFreemium.getPirFreemiumState())
            _viewState.update { it.copy(cta = cta) }

            if (cta != null && cta != lastImpressionCta) {
                lastImpressionCta = cta
                pixelSender.reportFreemiumSettingsEntryPointImpression(cta)
            }
        }
    }

    fun onEntryPointClicked() {
        val cta = _viewState.value.cta ?: return
        val entryPoint = when (cta) {
            PirFreemiumCtaState.START_FREE_SCAN -> PirFreeScanEntryPoint.APP_SETTINGS_PRE_SCAN
            PirFreemiumCtaState.VIEW_SCAN_RESULTS -> PirFreeScanEntryPoint.APP_SETTINGS_POST_SCAN
        }

        pixelSender.reportFreemiumSettingsEntryPointClicked(cta)
        viewModelScope.launch { command.send(Command.OpenPirDashboard(entryPoint)) }
    }

    // a saved profile means a scan is underway, and the dashboard shows its results as they arrive
    private suspend fun resolveCta(freemiumState: PirFreemiumState): PirFreemiumCtaState? = when (freemiumState) {
        PirFreemiumState.NOT_ELIGIBLE -> null
        PirFreemiumState.USED -> PirFreemiumCtaState.VIEW_SCAN_RESULTS
        PirFreemiumState.ELIGIBLE -> if (hasSavedProfile()) PirFreemiumCtaState.VIEW_SCAN_RESULTS else PirFreemiumCtaState.START_FREE_SCAN
    }

    private suspend fun hasSavedProfile(): Boolean = withContext(dispatcherProvider.io()) { pirFreemiumDataStore.didActivate }
}
