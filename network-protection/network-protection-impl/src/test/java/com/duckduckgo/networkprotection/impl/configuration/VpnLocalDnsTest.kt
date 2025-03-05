package com.duckduckgo.networkprotection.impl.configuration

import android.annotation.SuppressLint
import com.duckduckgo.common.test.json.JSONObjectAdapter
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.impl.VpnRemoteFeatures
import com.squareup.moshi.Moshi
import java.net.InetAddress
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Test

@SuppressLint("DenyListedApi") // setRawStoredState to setup test
class VpnLocalDnsTest {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
    private val feature = FakeFeatureToggleFactory.create(VpnRemoteFeatures::class.java)
        .apply {
            localDNS().setRawStoredState(
                Toggle.State(
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
                                ],
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
        }
    private val defaultDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            TODO("Not yet implemented")
        }
    }

    private val vpnLocalDns = VpnLocalDnsModule.provideVpnLocalDns(feature, moshi, defaultDns)

    @Test
    fun `controller DNS lookup resolves to controller IPs`() {
        val dnsResponse = vpnLocalDns.lookup("controller.netp.duckduckgo.com").map { it.hostAddress }
        val expected = listOf("1.2.3.4", "1.2.2.2")

        assertEquals(expected, dnsResponse)
    }

    @Test(expected = NotImplementedError::class)
    fun `general DNS lookup is resolved by default DNS`() {
        vpnLocalDns.lookup("one.one.one").map { it.hostAddress }
    }

    @Test
    fun `controller DNS lookup returns fallback when remote entries not available`() {
        feature.localDNS().setRawStoredState(Toggle.State(settings = ""))

        val dnsResponse = vpnLocalDns.lookup("controller.netp.duckduckgo.com").map { it.hostAddress }
        val expected = listOf("20.253.26.112", "20.93.77.32")

        assertEquals(expected, dnsResponse)
    }

    @Test
    fun `controller DNS lookup returns fallback when remote entries not present`() {
        feature.localDNS().setRawStoredState(
            Toggle.State(
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

        assertEquals(expected, dnsResponse)
    }
}
