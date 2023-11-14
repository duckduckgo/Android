package com.duckduckgo.networkprotection.impl.connectionclass

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.prefs.FakeVpnSharedPreferencesProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionQualityStoreTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var connectionQualityStore: ConnectionQualityStore

    @Before
    fun setup() {
        connectionQualityStore = ConnectionQualityStore(FakeVpnSharedPreferencesProvider(), coroutineRule.testDispatcherProvider)
    }

    @Test
    fun saveConnectionLatency() = runTest {
        connectionQualityStore.saveConnectionLatency(100)

        assertEquals(100, connectionQualityStore.getConnectionLatency())
    }
}
