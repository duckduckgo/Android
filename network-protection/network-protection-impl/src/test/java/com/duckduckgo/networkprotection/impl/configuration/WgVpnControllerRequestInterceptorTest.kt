package com.duckduckgo.networkprotection.impl.configuration

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.networkprotection.impl.fakes.FakeNetPWaitlistDataStore
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.networkprotection.impl.waitlist.store.RealNetPWaitlistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class WgVpnControllerRequestInterceptorTest {

    private val coroutineRule = CoroutineTestRule()
    private val appBuildConfig: AppBuildConfig = mock()
    private lateinit var netPWaitlistRepository: NetPWaitlistRepository
    private lateinit var interceptor: WgVpnControllerRequestInterceptor

    @Before
    fun setup() {
        netPWaitlistRepository = RealNetPWaitlistRepository(
            FakeNetPWaitlistDataStore(),
            coroutineRule.testDispatcherProvider,
            coroutineRule.testScope,
        )

        interceptor = WgVpnControllerRequestInterceptor(
            netPWaitlistRepository,
            appBuildConfig,
        )
    }

    @Test
    fun whenInterceptNotNetPUrlThenDoNothing() {
        val chain = FakeChain("https://this.is.not.the.url/servers")

        val headers = interceptor.intercept(chain).headers

        assertTrue(headers.size == 0)
    }

    @Test
    fun whenInterceptServersCallThenAddAuthHeader() {
        val chain = FakeChain("https://staging.netp.duckduckgo.com/servers")

        val headers = interceptor.intercept(chain).headers

        assertTrue(headers.size == 1)
        assertFalse(headers.map { it.first }.any { it.contains("NetP-Debug-Code") })
        assertTrue(headers.map { it.first }.any { it.contains("Authorization") })
    }

    @Test
    fun whenInterceptRegisterCallThenAddAuthHeader() {
        val chain = FakeChain("https://staging.netp.duckduckgo.com/register")

        val headers = interceptor.intercept(chain).headers

        assertTrue(headers.size == 1)
        assertFalse(headers.map { it.first }.any { it.contains("NetP-Debug-Code") })
        assertTrue(headers.map { it.first }.any { it.contains("Authorization") })
    }

    @Test
    fun whenInterceptServersCallInternalBuildThenAddAuthAndDebugHeaders() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        val chain = FakeChain("https://staging.netp.duckduckgo.com/servers")

        val headers = interceptor.intercept(chain).headers

        assertTrue(headers.size == 2)
        assertTrue(headers.map { it.first }.any { it.contains("NetP-Debug-Code") })
        assertTrue(headers.map { it.first }.any { it.contains("Authorization") })
    }

    @Test
    fun whenInterceptRegisterCallInternalBuildThenAddAuthAndDebugHeaders() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        val chain = FakeChain("https://staging.netp.duckduckgo.com/register")

        val headers = interceptor.intercept(chain).headers

        assertTrue(headers.size == 2)
        assertTrue(headers.map { it.first }.any { it.contains("NetP-Debug-Code") })
        assertTrue(headers.map { it.first }.any { it.contains("Authorization") })
    }

    @Test
    fun whenInterceptServersCallPlayBuildThenAddAuthHeader() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        val chain = FakeChain("https://staging.netp.duckduckgo.com/servers")

        val headers = interceptor.intercept(chain).headers

        assertTrue(headers.size == 1)
        assertFalse(headers.map { it.first }.any { it.contains("NetP-Debug-Code") })
        assertTrue(headers.map { it.first }.any { it.contains("Authorization") })
    }

    @Test
    fun whenInterceptRegisterCallPlayBuildThenAddAuthHeader() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        val chain = FakeChain("https://staging.netp.duckduckgo.com/register")

        val headers = interceptor.intercept(chain).headers

        assertTrue(headers.size == 1)
        assertFalse(headers.map { it.first }.any { it.contains("NetP-Debug-Code") })
        assertTrue(headers.map { it.first }.any { it.contains("Authorization") })
    }
}
