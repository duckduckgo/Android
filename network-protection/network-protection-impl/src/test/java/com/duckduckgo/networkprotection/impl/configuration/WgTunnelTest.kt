package com.duckduckgo.networkprotection.impl.configuration

import android.os.Build.VERSION
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.vpn.prefs.FakeVpnSharedPreferencesProvider
import com.duckduckgo.networkprotection.impl.store.RealNetworkProtectionRepository
import com.duckduckgo.networkprotection.store.RealNetworkProtectionPrefs
import com.wireguard.config.InetAddresses
import java.lang.reflect.Field
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WgTunnelTest {

    private val wgServerApi: WgServerApi = mock()
    private val serverData = WgServerApi.WgServerData(
        serverName = "name",
        publicKey = "public key",
        publicEndpoint = "1.1.1.1:443",
        address = "10.0.0.1/32",
        location = "Furadouro",
        gateway = "10.1.1.1",
    )
    private lateinit var wgTunnel: WgTunnel

    @Before
    fun setup() {
        val networkProtectionPrefs = RealNetworkProtectionPrefs(FakeVpnSharedPreferencesProvider())
        val networkProtectionRepository = RealNetworkProtectionRepository(networkProtectionPrefs)
        val deviceKeys = RealDeviceKeys(networkProtectionRepository, WgKeyPairGenerator())
        setFinalStatic(VERSION::class.java.getField("SDK_INT"), 29)

        runBlocking {
            whenever(wgServerApi.registerPublicKey(eq(deviceKeys.publicKey)))
                .thenReturn(serverData.copy(publicKey = deviceKeys.publicKey))
        }

        wgTunnel = RealWgTunnel(deviceKeys, wgServerApi)
    }

    @Test
    fun establishThenReturnWgTunnelData() = runTest {
        val actual = wgTunnel.establish().getOrThrow().copy(userSpaceConfig = "")
        val expected = WgTunnel.WgTunnelData(
            serverName = serverData.serverName,
            userSpaceConfig = "",
            serverLocation = serverData.location,
            serverIP = serverData.publicEndpoint.substringBefore(":"),
            gateway = serverData.gateway,
            tunnelAddress = mapOf(
                InetAddresses.parse(serverData.address.substringBefore("/")) to serverData.address.substringAfter("/").toInt(),
            ),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun establishErrorThenLogError() = runTest {
        whenever(wgServerApi.registerPublicKey(any())).thenReturn(serverData)

        assertNull(wgTunnel.establish().getOrNull())
    }

    @Throws(Exception::class)
    fun setFinalStatic(field: Field, newValue: Any?) {
        field.setAccessible(true)
        field.set(null, newValue)
    }
}
