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

package com.duckduckgo.sync.internal.ui

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SyncAuditLogDebugViewModel @Inject constructor(
    private val syncStore: SyncStore,
    private val syncFeature: SyncFeature,
    private val auditLogDebugReport: AuditLogDebugReport,
    private val auditLogCheckDebugControls: AuditLogCheckDebugControls,
    private val otherDevicesDebugTool: OtherDevicesDebugTool,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    data class ViewState(
        val loginAuditLogEnabled: Boolean = false,
        val loginNotificationEnabled: Boolean = false,
        val serverLogResult: String = "",
        val unseenLoginsCount: String = "",
        val unseenLoginsResult: String = "",
        val otherDevices: List<ConnectedDevice> = emptyList(),
        val otherDevicesListError: String? = null,
        val otherDevicesResult: String = "",
    )

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): StateFlow<ViewState> = viewState.asStateFlow()

    init {
        viewModelScope.launch(dispatchers.io()) {
            refreshFlagsAndCount()
            refreshOtherDevices()
        }
    }

    fun onLoginAuditLogFlagChanged(enabled: Boolean) = setFlag("loginAuditLog", syncFeature.loginAuditLog(), enabled)

    fun onLoginNotificationFlagChanged(enabled: Boolean) = setFlag("loginNotification", syncFeature.loginNotification(), enabled)

    fun onReadServerLogClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val token = syncStore.token
            if (token.isNullOrEmpty()) {
                viewState.update { it.copy(serverLogResult = "Not signed in") }
                return@launch
            }
            viewState.update { it.copy(serverLogResult = "Loading…") }
            val text = auditLogDebugReport.fetchAndDescribe(token, syncStore.deviceId)
            viewState.update { it.copy(serverLogResult = text) }
        }
    }

    fun onFindUnseenAuditEntriesClicked() = runUnseenLoginsAction { auditLogCheckDebugControls.findUnseen() }

    fun onMarkAllAuditLoginsSeenClicked() = runUnseenLoginsAction { auditLogCheckDebugControls.markAllAsSeen() }

    fun onSimulateLoginClicked() = runOtherDevicesAction { otherDevicesDebugTool.simulateLogin() }

    fun onLogOutOtherDeviceClicked(device: ConnectedDevice) = runOtherDevicesAction { otherDevicesDebugTool.logOut(device) }

    @SuppressLint("DenyListedApi")
    private fun setFlag(name: String, toggle: Toggle, enabled: Boolean) {
        viewModelScope.launch(dispatchers.io()) {
            logcat { "Sync-AuditLog: setting $name flag = $enabled" }
            val state = toggle.getRawStoredState()?.copy(enable = enabled, remoteEnableState = enabled)
                ?: Toggle.State(enable = enabled, remoteEnableState = enabled)
            toggle.setRawStoredState(state)
            refreshFlagsAndCount()
        }
    }

    private fun runUnseenLoginsAction(action: suspend () -> String) {
        viewModelScope.launch(dispatchers.io()) {
            viewState.update { it.copy(unseenLoginsResult = "Checking…") }
            val text = action()
            viewState.update { it.copy(unseenLoginsResult = text, unseenLoginsCount = auditLogCheckDebugControls.unseenCount()) }
        }
    }

    private fun runOtherDevicesAction(action: suspend () -> String) {
        viewModelScope.launch(dispatchers.io()) {
            viewState.update { it.copy(otherDevicesResult = "Working…") }
            val text = action()
            viewState.update { it.copy(otherDevicesResult = text) }
            refreshOtherDevices()
        }
    }

    private fun refreshOtherDevices() {
        val result = otherDevicesDebugTool.otherDevices()
        viewState.update { it.copy(otherDevices = result.devices, otherDevicesListError = result.error) }
    }

    private suspend fun refreshFlagsAndCount() {
        viewState.update {
            it.copy(
                loginAuditLogEnabled = syncFeature.loginAuditLog().isEnabled(),
                loginNotificationEnabled = syncFeature.loginNotification().isEnabled(),
                unseenLoginsCount = auditLogCheckDebugControls.unseenCount(),
            )
        }
    }
}
