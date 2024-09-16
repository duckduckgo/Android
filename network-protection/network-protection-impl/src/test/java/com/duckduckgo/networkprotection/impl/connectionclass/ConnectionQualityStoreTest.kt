package com.duckduckgo.networkprotection.impl.connectionclass

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ConnectionQualityStoreTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var connectionQualityStore: ConnectionQualityStore

    @Before
    fun setup() {
        connectionQualityStore = ConnectionQualityStore(FakeSharedPreferencesProvider(), coroutineRule.testDispatcherProvider)
    }

    @Test
    fun saveConnectionLatency() = runTest {
        connectionQualityStore.saveConnectionLatency(100)

        assertEquals(100, connectionQualityStore.getConnectionLatency())
    }
}
