package com.duckduckgo.networkprotection.impl.configuration

import android.os.Build.VERSION
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.networkprotection.impl.config.NetPDefaultConfigProvider
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
        AllowedIPs = ${serverData.allowedIPs}
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
            whenever(wgServerApi.registerPublicKey(eq(keys.publicKey.toBase64())))
                .thenReturn(serverData.copy(publicKey = keys.publicKey.toBase64()))
        }

        wgTunnel = RealWgTunnel(wgServerApi, netPDefaultConfigProvider)
    }

    @Test
    fun establishThenReturnWgTunnelData() = runTest {
        val actual = wgTunnel.establish(keys).getOrThrow()
        val expected = Config.parse(BufferedReader(StringReader(wgQuickConfig)))

        assertEquals(expected, actual)
    }

    @Test
    fun establishErrorThenLogError() = runTest {
        whenever(wgServerApi.registerPublicKey(any())).thenReturn(serverData)

        assertNull(wgTunnel.establish(keys).getOrNull())
    }

    @Test
    fun withNoKeysEstablishErrorThenLogError() = runTest {
        whenever(wgServerApi.registerPublicKey(any())).thenReturn(serverData)

        assertNull(wgTunnel.establish().getOrNull())
    }

    @Throws(Exception::class)
    fun setFinalStatic(field: Field, newValue: Any?) {
        field.setAccessible(true)
        field.set(null, newValue)
    }
}
