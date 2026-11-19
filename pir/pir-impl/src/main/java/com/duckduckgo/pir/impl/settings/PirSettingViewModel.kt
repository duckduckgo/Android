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

package com.duckduckgo.pir.impl.settings

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.pir.api.PirFeature
import com.duckduckgo.pir.api.dashboard.PirFeatureState
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.settings.PirSettingViewModel.Command.OpenPirDesktop
import com.duckduckgo.pir.impl.settings.PirSettingViewModel.ViewState.PirState
import com.duckduckgo.pir.impl.settings.PirSettingViewModel.ViewState.PirState.Enabled.Type
import com.duckduckgo.pir.impl.settings.PirSettingViewModel.ViewState.PirState.Enabled.Type.DASHBOARD
import com.duckduckgo.pir.impl.settings.PirSettingViewModel.ViewState.PirState.Enabled.Type.DESKTOP
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.subscriptions.api.Product.PIR
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class PirSettingViewModel @Inject constructor(
    private val pixelSender: PirPixelSender,
    private val subscriptions: Subscriptions,
    private val pirFeature: PirFeature,
    private val pirRepository: PirRepository,
) : ViewModel(), DefaultLifecycleObserver {

    sealed class Command {
        data object OpenPirDesktop : Command()
        data object OpenPirDashboard : Command()
        data object ShowPirUnavailableDialog : Command()
    }

    private val pirStateJob = ConflatedJob()
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    data class ViewState(val pirState: PirState = PirState.Hidden) {

        sealed class PirState {

            data object Hidden : PirState()
            data class Enabled(val type: Type, val isOn: Boolean) : PirState() {
                enum class Type {
                    DESKTOP,
                    DASHBOARD,
                }
            }

            data object Disabled : PirState()
        }
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    fun onPir(type: Type) {
        pixelSender.reportAppSettingsClick()

        viewModelScope.launch {
            val command = when (type) {
                DESKTOP -> OpenPirDesktop
                DASHBOARD -> {
                    when (pirFeature.getPirFeatureState()) {
                        PirFeatureState.ENABLED -> Command.OpenPirDashboard
                        PirFeatureState.DISABLED -> OpenPirDesktop
                        PirFeatureState.NOT_AVAILABLE -> Command.ShowPirUnavailableDialog
                    }
                }
            }
            sendCommand(command)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        pirStateJob += subscriptions.getEntitlements().map { entitlements -> entitlements.any { it.product == PIR.value } }
            .distinctUntilChanged()
            .onEach { hasValidEntitlement ->

                val subscriptionStatus = subscriptions.getSubscriptionStatus()

                val pirState = getPirState(hasValidEntitlement, subscriptionStatus)

                _viewState.update { it.copy(pirState = pirState) }
            }
            .launchIn(viewModelScope)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        pirStateJob.cancel()
    }

    private suspend fun getPirState(
        hasValidEntitlement: Boolean,
        subscriptionStatus: SubscriptionStatus,
    ): PirState {
        return when (subscriptionStatus) {
            SubscriptionStatus.UNKNOWN -> PirState.Hidden

            SubscriptionStatus.INACTIVE,
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.WAITING,
            -> {
                if (isPirAvailable()) {
                    PirState.Disabled
                } else {
                    PirState.Hidden
                }
            }

            SubscriptionStatus.AUTO_RENEWABLE,
            SubscriptionStatus.NOT_AUTO_RENEWABLE,
            SubscriptionStatus.GRACE_PERIOD,
            -> {
                if (hasValidEntitlement) {
                    when (pirFeature.getPirFeatureState()) {
                        PirFeatureState.ENABLED -> PirState.Enabled(DASHBOARD, isOn = hasUserProfile())
                        PirFeatureState.NOT_AVAILABLE -> PirState.Enabled(DASHBOARD, isOn = false)
                        PirFeatureState.DISABLED -> PirState.Enabled(DESKTOP, isOn = true)
                    }
                } else {
                    PirState.Hidden
                }
            }
        }
    }

    private suspend fun isPirAvailable(): Boolean {
        return subscriptions.getAvailableProducts().contains(PIR)
    }

    private suspend fun hasUserProfile(): Boolean {
        return try {
            pirRepository.hasValidUserProfileQueries()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(ERROR) { "PIR-SETTINGS: Failed to read user profile: $e" }
            false
        }
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }
}
