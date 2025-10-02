package com.duckduckgo.networkprotection.impl.configuration

import android.annotation.SuppressLint
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.impl.VpnRemoteFeatures
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import junit.framework.TestCase.assertEquals
import okhttp3.Dns
import org.junit.Assert
import org.junit.Test
import java.net.InetAddress

@SuppressLint("DenyListedApi") // setRawStoredState
class VpnLocalDnsImplTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val defaultDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            TODO("Not yet implemented")
        }
    }
    private val defaultState = Toggle.State(
        remoteEnableState = false,
        settings = """
            {
                "domains": {
                    "controller.netp.duckduckgo.com": [
                        {
                            "address": "1.2.3.4",
                            "region": "use"
                        },
                        {
                            "address": "1.2.2.2",
                            "region": "eun"
                        }
                    ]
                }
            }
        """.trimIndent(),
    )
    private val vpnRemoteFeatures = FakeFeatureToggleFactory.create(VpnRemoteFeatures::class.java).apply {
        localVpnControllerDns().setRawStoredState(defaultState)
    }
    private val vpnLocalDns = VpnLocalDnsModule.provideVpnLocalDns(vpnRemoteFeatures, moshi, defaultDns)

    @Test(expected = NotImplementedError::class)
    fun `lookup uses default DNS when feature is disabled and looking up domains other than VPN controller`() {
        vpnRemoteFeatures.localVpnControllerDns().setRawStoredState(defaultState.copy(remoteEnableState = false))

        assertEquals(emptyList<InetAddress>(), vpnLocalDns.lookup("not-controller.netp.duckduckgo.com"))
    }

    @Test(expected = NotImplementedError::class)
    fun `lookup uses default DNS when feature is disabled and looking up VPN controller domains`() {
        vpnRemoteFeatures.localVpnControllerDns().setRawStoredState(defaultState.copy(remoteEnableState = false))

        assertEquals(emptyList<InetAddress>(), vpnLocalDns.lookup("controller.netp.duckduckgo.com"))
    }

    @Test(expected = NotImplementedError::class)
    fun `lookup uses default DNS when feature is enabled and looking up domains other than VPN controller`() {
        vpnRemoteFeatures.localVpnControllerDns().setRawStoredState(defaultState.copy(remoteEnableState = true))

        assertEquals(emptyList<InetAddress>(), vpnLocalDns.lookup("not-controller.netp.duckduckgo.com"))
    }

    @Test
    fun `lookup uses in-app DNS when feature is enabled and looking up VPN controller domains`() {
        vpnRemoteFeatures.localVpnControllerDns().setRawStoredState(defaultState.copy(remoteEnableState = true))

        assertEquals(
            listOf(InetAddress.getByName("1.2.3.4"), InetAddress.getByName("1.2.2.2")),
            vpnLocalDns.lookup("controller.netp.duckduckgo.com"),
        )
    }

    @Test
    fun `controller DNS lookup returns fallback when remote entries not available`() {
        vpnRemoteFeatures.localVpnControllerDns().setRawStoredState(Toggle.State(remoteEnableState = true, settings = ""))

        val dnsResponse = vpnLocalDns.lookup("controller.netp.duckduckgo.com").map { it.hostAddress }
        val expected = listOf("20.253.26.112", "20.93.77.32")

        Assert.assertEquals(expected, dnsResponse)
    }

    @Test
    fun `controller DNS lookup returns fallback when remote entries not present`() {
        vpnRemoteFeatures.localVpnControllerDns().setRawStoredState(
            Toggle.State(
                remoteEnableState = true,
                settings = """
                        {
                            "domains": {
                                "fake.controller.duckduckgo.com": [
                                    {
                                        "address": "1.1.1.1",
                                        "region": "aus"
                                    }
                                ]
                            }
                        }
                """.trimIndent(),
            ),
        )

        val dnsResponse = vpnLocalDns.lookup("controller.netp.duckduckgo.com").map { it.hostAddress }
        val expected = listOf("20.253.26.112", "20.93.77.32")

        Assert.assertEquals(expected, dnsResponse)
    }
}
