package com.duckduckgo.remote.messaging.internal.feature

import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.remote.messaging.internal.setting.RmfInternalSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class RmfStagingEnvInterceptorTest {

    private val rmfInternalSettings = FakeFeatureToggleFactory.create(RmfInternalSettings::class.java)
    private val interceptor = RmfStagingEnvInterceptor(rmfInternalSettings)

    @Test
    fun interceptEndpointWhenEnabled() {
        rmfInternalSettings.useStatingEndpoint().setRawStoredState(State(enable = true))

        val chain = FakeChain(RMF_URL_V1)
        val response = interceptor.intercept(chain)

        assertEquals(RMF_STAGING_URL, response.request.url.toString())

        val chain2 = FakeChain(RMF_URL_V2)
        val response2 = interceptor.intercept(chain2)

        assertEquals(RMF_STAGING_URL, response2.request.url.toString())
    }

    @Test
    fun interceptNoopWhenDisabled() {
        rmfInternalSettings.useStatingEndpoint().setRawStoredState(State(enable = false))

        val chain = FakeChain(RMF_URL_V1)
        val response = interceptor.intercept(chain)

        assertEquals(RMF_URL_V1, response.request.url.toString())

        val chain2 = FakeChain(RMF_URL_V2)
        val response2 = interceptor.intercept(chain2)

        assertEquals(RMF_URL_V2, response2.request.url.toString())
    }

    @Test
    fun interceptIgnoreUnknownEndpointWhenEnabled() {
        rmfInternalSettings.useStatingEndpoint().setRawStoredState(State(enable = true))

        val chain = FakeChain(UNKNOWN_URL)
        val response = interceptor.intercept(chain)

        assertEquals(UNKNOWN_URL, response.request.url.toString())
    }

    @Test
    fun interceptIgnoreUnknownEndpointWhenDisabled() {
        rmfInternalSettings.useStatingEndpoint().setRawStoredState(State(enable = false))

        val chain = FakeChain(UNKNOWN_URL)
        val response = interceptor.intercept(chain)

        assertEquals(UNKNOWN_URL, response.request.url.toString())
    }
}

private const val RMF_URL_V1 = "https://staticcdn.duckduckgo.com/remotemessaging/config/v1/android-config.json"
private const val RMF_URL_V2 = "https://staticcdn.duckduckgo.com/remotemessaging/config/v2/android-config.json"
private const val RMF_STAGING_URL = "https://staticcdn.duckduckgo.com/remotemessaging/config/staging/android-config.json"
private const val UNKNOWN_URL = "https://unknown.com/remotemessaging/config/staging/android-config.json"
