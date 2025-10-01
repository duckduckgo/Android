package com.duckduckgo.networkprotection.impl.configuration

import android.os.Build.VERSION
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.networkprotection.impl.config.NetPDefaultConfigProvider
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.Mode.FailureRecovery
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import java.io.BufferedReader
import java.io.StringReader
import java.lang.reflect.Field
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class WgTunnelTest {

    private val wgServerApi: WgServerApi = mock()
    private val netPDefaultConfigProvider: NetPDefaultConfigProvider = object : NetPDefaultConfigProvider {}
    private lateinit var wgTunnelStore: WgTunnelStore
    private val keys = KeyPair()
    private val serverData = WgServerApi.WgServerData(
        serverName = "name",
        publicKey = "public key",
        publicEndpoint = "1.1.1.1:443",
        address = "10.0.0.1/32",
        location = "Furadouro",
        gateway = "10.1.1.1",
    )

    private val wgQuickConfig = """
        [Interface]
        Address = ${serverData.address}
        DNS = ${serverData.gateway}
        MTU = 1280
        PrivateKey = ${keys.privateKey.toBase64()}

        [Peer]
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = ${serverData.publicEndpoint}
        Name = ${serverData.serverName}
        Location = ${serverData.location}
        PublicKey = ${keys.publicKey.toBase64()}
    """.trimIndent()

    private lateinit var wgTunnel: WgTunnel

    @Before
    fun setup() {
        setFinalStatic(VERSION::class.java.getField("SDK_INT"), 29)

        runBlocking {
            whenever(wgServerApi.registerPublicKey(eq(keys.publicKey.toBase64()), isNull()))
                .thenReturn(serverData.copy(publicKey = keys.publicKey.toBase64()))
        }
        wgTunnelStore = WgTunnelStore(FakeSharedPreferencesProvider())
        wgTunnel = RealWgTunnel(wgServerApi, netPDefaultConfigProvider, wgTunnelStore)
    }

    @Test
    fun establishThenReturnWgTunnelData() = runTest {
        val actual = wgTunnel.createWgConfig(keys).getOrThrow()
        val expected = Config.parse(BufferedReader(StringReader(wgQuickConfig)))

        assertEquals(expected, actual)
    }

    @Test
    fun establishErrorThenLogError() = runTest {
        whenever(wgServerApi.registerPublicKey(any(), isNull())).thenReturn(serverData)

        assertNull(wgTunnel.createWgConfig(keys).getOrNull())
    }

    @Test
    fun withNoKeysEstablishErrorThenLogError() = runTest {
        whenever(wgServerApi.registerPublicKey(any(), isNull())).thenReturn(serverData)

        assertNull(wgTunnel.createWgConfig().getOrNull())
    }

    @Test
    fun whenTunnelIsMarkedAsUnhealthyAndCreateWgConfigThenUpdateStateToFailureRecovery() = runTest {
        whenever(wgServerApi.registerPublicKey(any(), eq(FailureRecovery(currentServer = "name")))).thenReturn(serverData)
        wgTunnelStore.wireguardConfig = Config.parse(BufferedReader(StringReader(wgQuickConfig)))

        wgTunnel.markTunnelUnhealthy()
        assertNull(wgTunnel.createWgConfig(KeyPair()).getOrNull())

        verify(wgServerApi).registerPublicKey(any(), eq(FailureRecovery(currentServer = "name")))
    }

    @Test
    fun whenTunnelIsMarkAsUnhealthyAndCreateAndSetWgConfigThenResetTunnelHealth() = runTest {
        whenever(wgServerApi.registerPublicKey(any(), isNull())).thenReturn(serverData)

        wgTunnel.markTunnelUnhealthy()
        assertNull(wgTunnel.createAndSetWgConfig(KeyPair()).getOrNull())

        verify(wgServerApi).registerPublicKey(any(), isNull())
    }

    @Test
    fun whenTunnelIsMarkedAsUnhealthyTheHealthyAndCreateWgConfigThenResetTunnelHealth() = runTest {
        whenever(wgServerApi.registerPublicKey(any(), isNull())).thenReturn(serverData)

        wgTunnel.markTunnelUnhealthy()
        wgTunnel.markTunnelHealthy()
        assertNull(wgTunnel.createWgConfig(KeyPair()).getOrNull())

        verify(wgServerApi).registerPublicKey(any(), isNull())
    }

    @Throws(Exception::class)
    fun setFinalStatic(field: Field, newValue: Any?) {
        field.setAccessible(true)
        field.set(null, newValue)
    }
}
