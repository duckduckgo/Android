package com.duckduckgo.networkprotection.impl.subscription.settings

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus
import com.duckduckgo.networkprotection.impl.subscription.settings.NetworkProtectionSettingsState.NetPSettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NetworkProtectionSettingsStateImplTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var networkProtectionSettingsState: NetworkProtectionSettingsStateImpl
    private lateinit var fakeNetworkProtectionState: FakeNetworkProtectionState
    private lateinit var fakeNetpSubscriptionManager: FakeNetpSubscriptionManager

    @Before
    fun setUp() {
        fakeNetworkProtectionState = FakeNetworkProtectionState()
        fakeNetpSubscriptionManager = FakeNetpSubscriptionManager()

        networkProtectionSettingsState = NetworkProtectionSettingsStateImpl(
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            networkProtectionState = fakeNetworkProtectionState,
            netpSubscriptionManager = fakeNetpSubscriptionManager,
        )
    }

    @Test
    fun `when VpnStatus is active then returns subscribed`() = runTest {
        fakeNetpSubscriptionManager.setVpnStatus(VpnStatus.ACTIVE)

        networkProtectionSettingsState.getNetPSettingsStateFlow().test {
            assertEquals(NetPSettingsState.Visible.Subscribed, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when VpnStatus is inactive then returns expired`() = runTest {
        fakeNetpSubscriptionManager.setVpnStatus(VpnStatus.INACTIVE)

        networkProtectionSettingsState.getNetPSettingsStateFlow().test {
            assertEquals(NetPSettingsState.Visible.Expired, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when VpnStatus is expired then returns expired`() = runTest {
        fakeNetpSubscriptionManager.setVpnStatus(VpnStatus.EXPIRED)

        networkProtectionSettingsState.getNetPSettingsStateFlow().test {
            assertEquals(NetPSettingsState.Visible.Expired, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when VpnStatus is activating then returns waiting`() = runTest {
        fakeNetpSubscriptionManager.setVpnStatus(VpnStatus.WAITING)

        networkProtectionSettingsState.getNetPSettingsStateFlow().test {
            assertEquals(NetPSettingsState.Visible.Activating, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when VpnStatus is signed out then returns hidden`() = runTest {
        fakeNetpSubscriptionManager.setVpnStatus(VpnStatus.SIGNED_OUT)

        networkProtectionSettingsState.getNetPSettingsStateFlow().test {
            assertEquals(NetPSettingsState.Hidden, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `when VpnStatus is ineligible then returns hidden`() = runTest {
        fakeNetpSubscriptionManager.setVpnStatus(VpnStatus.INELIGIBLE)

        networkProtectionSettingsState.getNetPSettingsStateFlow().test {
            assertEquals(NetPSettingsState.Hidden, awaitItem())
            awaitComplete()
        }
    }
}

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
