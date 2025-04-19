package com.duckduckgo.networkprotection.impl.configuration

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NetpControllerRequestInterceptorTest {

    private val appBuildConfig: AppBuildConfig = mock()
    private val subscriptions: Subscriptions = mock()
    private lateinit var interceptor: NetpControllerRequestInterceptor

    @Before
    fun setup() {
        runBlocking {
            // default values
            whenever(subscriptions.getAccessToken()).thenReturn(null)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        }

        interceptor = NetpControllerRequestInterceptor(
            appBuildConfig,
            subscriptions,
        )
    }

    @Test
    fun `when @AuthRequired annotation is not present then authorization header not is added`() {
        val nonAnnotatedMethod = FakeApiService::class.java.getMethod("endpointNotRequiringAuthentication")

        val chain = FakeChain(
            url = "https://this.is.not.the.url/servers",
            serviceMethod = nonAnnotatedMethod,
        )

        val headers = interceptor.intercept(chain).headers

        assertTrue(headers.size == 0)
    }

    @Test
    fun `when @AuthRequired annotation is not present and internal build then authorization header not is added`() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        val nonAnnotatedMethod = FakeApiService::class.java.getMethod("endpointNotRequiringAuthentication")

        val chain = FakeChain(
            url = "https://this.is.not.the.url/servers",
            serviceMethod = nonAnnotatedMethod,
        )

        val headers = interceptor.intercept(chain).headers

        assertTrue(headers.size == 0)
    }

    @Test
    fun `when @AuthRequired annotation is present and internal then authorization header is added`() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        val annotatedMethod = FakeApiService::class.java.getMethod("endpointRequiringAuthentication")
        val chain = FakeChain(
            url = "https://staging.netp.duckduckgo.com/servers",
            serviceMethod = annotatedMethod,
        )

        val headers = interceptor.intercept(chain).headers

        assertTrue(headers.size == 2)
        assertTrue(headers.map { it.first }.any { it.contains("NetP-Debug-Code") })
        assertTrue(headers.map { it.first }.any { it.contains("Authorization") })
    }

    @Test
    fun `when @AuthRequired annotation is present and not internal then netp debug code not added`() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        val annotatedMethod = FakeApiService::class.java.getMethod("endpointRequiringAuthentication")
        val chain = FakeChain(
            url = "https://staging.netp.duckduckgo.com/servers",
            serviceMethod = annotatedMethod,
        )

        val headers = interceptor.intercept(chain).headers

        assertTrue(headers.size == 1)
        assertFalse(headers.map { it.first }.any { it.contains("NetP-Debug-Code") })
        assertTrue(headers.map { it.first }.any { it.contains("Authorization") })
    }

    private interface FakeApiService {
        @AuthRequired
        fun endpointRequiringAuthentication()
        fun endpointNotRequiringAuthentication()
    }
}
