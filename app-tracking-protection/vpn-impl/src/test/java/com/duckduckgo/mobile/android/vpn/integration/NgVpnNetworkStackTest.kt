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

package com.duckduckgo.mobile.android.vpn.integration

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.app.tracking.AppTrackerDetector
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.feature.AppTpLocalFeature
import com.duckduckgo.mobile.android.vpn.network.FakeDnsProvider
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.vpn.network.api.*
import com.duckduckgo.vpn.network.api.AddressRR
import com.duckduckgo.vpn.network.api.DnsRR
import com.duckduckgo.vpn.network.api.VpnNetwork
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TRACKER_HOSTNAME = "api2.branch.com"

@RunWith(AndroidJUnit4::class)
class NgVpnNetworkStackTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val appBuildConfig: AppBuildConfig = mock()
    private val vpnNetwork: VpnNetwork = mock()
    private val runtime: Runtime = mock()
    private val appTrackerDetector: AppTrackerDetector = mock()
    private val trackingProtectionAppsRepository: TrackingProtectionAppsRepository = mock()
    private val appTpLocalFeature: AppTpLocalFeature = mock()
    private val deviceShieldPixels: DeviceShieldPixels = mock()

    private val verboseLoggingToggle: Toggle = mock()
    private val tunfd: ParcelFileDescriptor = mock()
    private val fakeVpnNetwork = FakeVpnNetwork()

    private lateinit var fakeDnsProvider: FakeDnsProvider
    private lateinit var ngVpnNetworkStack: NgVpnNetworkStack

    @Before
    fun setup() {
        whenever(vpnNetwork.create()).thenReturn(111)
        whenever(vpnNetwork.mtu()).thenReturn(1500)
        fakeDnsProvider = FakeDnsProvider()

        ngVpnNetworkStack = NgVpnNetworkStack(
            appBuildConfig,
            lazy { vpnNetwork },
            runtime,
            appTrackerDetector,
            trackingProtectionAppsRepository,
            appTpLocalFeature,
            deviceShieldPixels,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
            fakeDnsProvider,
        )
        whenever(appTpLocalFeature.verboseLogging()).thenReturn(verboseLoggingToggle)
        whenever(tunfd.fd).thenReturn(42)
    }

    @After
    fun tearDown() {
        fakeVpnNetwork.releaseStuckThreads()
    }

    private fun stackWith(network: VpnNetwork): NgVpnNetworkStack {
        return NgVpnNetworkStack(
            appBuildConfig,
            lazy { network },
            runtime,
            appTrackerDetector,
            trackingProtectionAppsRepository,
            appTpLocalFeature,
            deviceShieldPixels,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
            fakeDnsProvider,
        )
    }

    @Test
    fun whenOnCreateVpnWhileTunnelRunsThenTunnelThreadExitsBeforeContextIsDestroyed() {
        val stack = stackWith(fakeVpnNetwork)
        assertTrue(stack.onCreateVpn().isSuccess)
        assertTrue(stack.onStartVpn(tunfd).isSuccess)
        fakeVpnNetwork.awaitRunning()

        assertTrue(stack.onCreateVpn().isSuccess)

        assertEquals(
            listOf("create:1", "start:1", "run:1", "stop:1", "run-exited:1", "destroy:1", "create:2"),
            fakeVpnNetwork.events,
        )
    }

    @Test
    fun whenOnDestroyVpnWhileTunnelRunsThenTunnelThreadExitsBeforeContextIsDestroyed() {
        val stack = stackWith(fakeVpnNetwork)
        assertTrue(stack.onCreateVpn().isSuccess)
        assertTrue(stack.onStartVpn(tunfd).isSuccess)
        fakeVpnNetwork.awaitRunning()

        assertTrue(stack.onDestroyVpn().isSuccess)

        assertEquals(
            listOf("create:1", "start:1", "run:1", "stop:1", "run-exited:1", "destroy:1"),
            fakeVpnNetwork.events,
        )
    }

    @Test
    fun whenTunnelThreadNeverExitsThenStopFailsAfterBoundAndTheStuckPairIsDetachedWithoutDestroyingTheContext() {
        fakeVpnNetwork.ignoreStop = true
        val stack = stackWith(fakeVpnNetwork).apply { tunnelThreadJoinBoundMillis = 200 }
        assertTrue(stack.onCreateVpn().isSuccess)
        assertTrue(stack.onStartVpn(tunfd).isSuccess)
        fakeVpnNetwork.awaitRunning()

        val stopResult = stack.onStopVpn(VpnStopReason.RESTART)
        fakeVpnNetwork.ignoreStop = false
        val secondStopStartedAt = System.nanoTime()
        val secondStopResult = stack.onStopVpn(VpnStopReason.RESTART)
        val secondStopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - secondStopStartedAt)
        val destroyResult = stack.onDestroyVpn()

        assertTrue(stopResult.exceptionOrNull() is TunnelThreadStopTimeoutException)
        assertTrue(secondStopResult.isSuccess)
        assertTrue("second stop joined the stuck thread for ${secondStopMillis}ms", secondStopMillis < 100)
        assertTrue(destroyResult.isSuccess)
        // context 1 is leaked: stopped once, never destroyed, and never stopped again
        assertEquals(listOf("create:1", "start:1", "run:1", "stop:1"), fakeVpnNetwork.events)
        verify(deviceShieldPixels, times(1)).reportTunnelThreadJoinBoundExceeded()
    }

    @Test
    fun whenTheStuckTunnelThreadWasDetachedThenANewContextIsCreatedAndStarted() {
        fakeVpnNetwork.ignoreStop = true
        val stack = stackWith(fakeVpnNetwork).apply { tunnelThreadJoinBoundMillis = 200 }
        assertTrue(stack.onCreateVpn().isSuccess)
        assertTrue(stack.onStartVpn(tunfd).isSuccess)
        fakeVpnNetwork.awaitRunning()
        assertTrue(stack.onStopVpn(VpnStopReason.RESTART).exceptionOrNull() is TunnelThreadStopTimeoutException)
        fakeVpnNetwork.ignoreStop = false

        assertTrue(stack.onCreateVpn().isSuccess)
        assertTrue(stack.onStartVpn(tunfd).isSuccess)
        fakeVpnNetwork.awaitRunning()
        assertTrue(stack.onStopVpn(VpnStopReason.RESTART).isSuccess)

        assertEquals(
            listOf("create:1", "start:1", "run:1", "stop:1", "create:2", "start:2", "run:2", "stop:2", "run-exited:2"),
            fakeVpnNetwork.events,
        )
        verify(deviceShieldPixels, times(1)).reportTunnelThreadJoinBoundExceeded()
    }

    @Test
    fun whenOnStartVpnWhileTunnelThreadAliveThenReturnFailure() {
        val stack = stackWith(fakeVpnNetwork)
        assertTrue(stack.onCreateVpn().isSuccess)
        assertTrue(stack.onStartVpn(tunfd).isSuccess)
        fakeVpnNetwork.awaitRunning()

        assertTrue(stack.onStartVpn(tunfd).isFailure)
        assertEquals(1, fakeVpnNetwork.events.count { it.startsWith("run:") })
    }

    @Test
    fun whenStartStopDestroyCreateStartThenNewTunnelRunsOnNewContext() {
        val stack = stackWith(fakeVpnNetwork)
        assertTrue(stack.onCreateVpn().isSuccess)
        assertTrue(stack.onStartVpn(tunfd).isSuccess)
        fakeVpnNetwork.awaitRunning()

        assertTrue(stack.onStopVpn(VpnStopReason.RESTART).isSuccess)
        assertTrue(stack.onDestroyVpn().isSuccess)
        assertTrue(stack.onCreateVpn().isSuccess)
        assertTrue(stack.onStartVpn(tunfd).isSuccess)
        fakeVpnNetwork.awaitRunning()

        assertEquals(
            listOf(
                "create:1", "start:1", "run:1",
                "stop:1", "run-exited:1", // onStopVpn
                "stop:1", "destroy:1", // onDestroyVpn signals stop again, harmless with no thread running
                "create:2", "start:2", "run:2",
            ),
            fakeVpnNetwork.events,
        )
        verify(deviceShieldPixels, never()).reportTunnelThreadJoinBoundExceeded()
    }

    @Test
    fun whenCheckingNeedsNetworkToPrepareThenFalse() {
        assertFalse(ngVpnNetworkStack.needsNetworkToPrepare)
    }

    @Test
    fun whenOnCreateVpnNoErrorThenReturnSuccess() {
        assertTrue(ngVpnNetworkStack.onCreateVpn().isSuccess)
    }

    @Test
    fun whenOnCreateVpnThenCreateVpnNetwork() {
        ngVpnNetworkStack.onCreateVpn()

        verify(vpnNetwork).create()
    }

    @Test
    fun whenOnCreateVpnMultipleTimesThenDestroyPreviousNetwork() {
        ngVpnNetworkStack.onCreateVpn()
        ngVpnNetworkStack.onCreateVpn()

        verify(vpnNetwork).stop(111)
        verify(vpnNetwork).destroy(111)
    }

    @Test
    fun whenOnCreateVpnMultipleTimesThenCreateVpnNetwork() {
        ngVpnNetworkStack.onCreateVpn()
        ngVpnNetworkStack.onCreateVpn()

        verify(vpnNetwork, times(2)).create()
    }

    @Test
    fun whenOnPrepareVpnThenReturnCorrectVpnTunnelConfig() = runTest {
        whenever(trackingProtectionAppsRepository.getExclusionAppsList()).thenReturn(listOf())

        val configResult = ngVpnNetworkStack.onPrepareVpn()
        assertTrue(configResult.isSuccess)

        val config = configResult.getOrThrow()
        assertEquals(1500, config.mtu)
        assertTrue(config.routes.isEmpty())
        assertTrue(config.dns.isEmpty())
        assertEquals(
            mapOf(
                InetAddress.getByName("10.0.0.2") to 32,
                InetAddress.getByName("fd00:1:fd00:1:fd00:1:fd00:1") to 128, // Add IPv6 Unique Local Address
            ),
            config.addresses,
        )
    }

    @Test
    fun whenSystemDnsAvailableThenOnlyConfigureSearchDomains() = runTest {
        fakeDnsProvider.mutableSystemDns.add(InetAddress.getLocalHost())
        fakeDnsProvider.searchDomain = "internal.com"
        whenever(trackingProtectionAppsRepository.getExclusionAppsList()).thenReturn(listOf())

        val configResult = ngVpnNetworkStack.onPrepareVpn()
        assertTrue(configResult.isSuccess)

        val config = configResult.getOrThrow()
        assertEquals(1500, config.mtu)
        assertTrue(config.routes.isEmpty())
        assertTrue(config.dns.isEmpty())
        assertEquals("internal.com", config.searchDomains)
        assertEquals(
            mapOf(
                InetAddress.getByName("10.0.0.2") to 32,
                InetAddress.getByName("fd00:1:fd00:1:fd00:1:fd00:1") to 128, // Add IPv6 Unique Local Address
            ),
            config.addresses,
        )
    }

    @Test
    fun whenOnEmFileErrorThenKillProcess() {
        ngVpnNetworkStack.onError(24, "EmFileError")

        verify(runtime).exit(0)
    }

    @Test
    fun whenOnAnyErrorOtherThanEmFileThenNoop() {
        for (error in 0..23) {
            ngVpnNetworkStack.onError(error, "SomeError")
            verify(runtime, never()).exit(0)
        }

        for (error in 25..200) {
            ngVpnNetworkStack.onError(error, "SomeError")
            verify(runtime, never()).exit(0)
        }
    }

    @Test
    fun whenOnExitThenKillProcess() {
        ngVpnNetworkStack.onExit("SomeError")

        verify(runtime).exit(0)
    }

    @Test
    fun whenIsAddressBlockedAndDomainIsTrackerThenReturnFalse() { // false because we don't want to block based on IP addresses
        val uid = 1200
        val tracker = AppTrackerDetector.AppTracker(
            "hostname",
            33,
            "AppDisplayName",
            "app.package.name",
            "AppName",
        )

        whenever(appTrackerDetector.evaluate(TRACKER_HOSTNAME, uid)).thenReturn(tracker)
        ngVpnNetworkStack.onDnsResolved(createDnsRecord(TRACKER_HOSTNAME, "1.1.1.1"))

        assertFalse(ngVpnNetworkStack.isAddressBlocked(AddressRR("1.1.1.1", uid)))
    }

    @Test
    fun whenIsAddressBlockedAndDomainIsNotTrackerThenReturnFalse() {
        val uid = 1200

        whenever(appTrackerDetector.evaluate(TRACKER_HOSTNAME, uid)).thenReturn(null)
        ngVpnNetworkStack.onDnsResolved(createDnsRecord(TRACKER_HOSTNAME, "1.1.1.1"))

        Assert.assertFalse(ngVpnNetworkStack.isAddressBlocked(AddressRR("1.1.1.1", uid)))
    }

    private fun createDnsRecord(
        domain: String,
        address: String,
    ): DnsRR {
        return DnsRR(
            time = 0,
            qName = domain,
            aName = domain,
            resource = address,
            ttl = 0,
        )
    }
}

/**
 * Native layer fake whose run() blocks until stop() is called for the same context, like netguard's jni_run.
 * With [ignoreStop] set, run() never returns on stop(), simulating a wedged tunnel thread.
 */
private class FakeVpnNetwork : VpnNetwork {
    val events: MutableList<String> = CopyOnWriteArrayList()

    @Volatile var ignoreStop = false

    private var nextContext = 0L
    private val stopLatches = mutableMapOf<Long, CountDownLatch>()
    private val releaseStuck = CountDownLatch(1)

    @Volatile private var running = CountDownLatch(1)

    override fun create(): Long = synchronized(this) {
        val context = ++nextContext
        stopLatches[context] = CountDownLatch(1)
        events += "create:$context"
        context
    }

    override fun start(contextId: Long, logLevel: VpnNetworkLog) {
        events += "start:$contextId"
    }

    override fun run(contextId: Long, tunfd: Int) {
        events += "run:$contextId"
        running.countDown()
        if (ignoreStop) {
            releaseStuck.await()
        } else {
            synchronized(this) { stopLatches.getValue(contextId) }.await()
        }
        events += "run-exited:$contextId"
    }

    override fun stop(contextId: Long) {
        events += "stop:$contextId"
        synchronized(this) { stopLatches[contextId] }?.countDown()
    }

    override fun destroy(contextId: Long) {
        events += "destroy:$contextId"
    }

    override fun mtu(): Int = 1500

    override fun addCallback(callback: VpnNetworkCallback?) {}

    fun awaitRunning() {
        assertTrue("tunnel thread never called run()", running.await(5, TimeUnit.SECONDS))
        running = CountDownLatch(1)
    }

    fun releaseStuckThreads() {
        releaseStuck.countDown()
    }
}
