package com.duckduckgo.networkprotection.impl.subscription.settings

import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

private class FakeNetworkProtectionState : NetworkProtectionState {
    override suspend fun isOnboarded(): Boolean = false
    override suspend fun isEnabled(): Boolean = false
    override suspend fun isRunning(): Boolean = false
    override fun start() {}
    override fun restart() {}
    override fun clearVPNConfigurationAndRestart() {}
    override suspend fun stop() {}
    override fun clearVPNConfigurationAndStop() {}
    override fun serverLocation(): String? = null
    override fun getConnectionStateFlow(): Flow<NetworkProtectionState.ConnectionState> = flowOf()
    override suspend fun getExcludedApps(): List<String> = emptyList()
}

private class FakeNetpSubscriptionManager : NetpSubscriptionManager {

    private var vpnStatusFlow: Flow<VpnStatus> = flowOf()

    override suspend fun getVpnStatus(): VpnStatus = vpnStatusFlow.first()
    override suspend fun vpnStatus(): Flow<VpnStatus> = vpnStatusFlow

    fun setVpnStatus(status: VpnStatus) {
        vpnStatusFlow = flowOf(status)
    }
}
