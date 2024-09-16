package com.duckduckgo.networkprotection.impl.connectionclass

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ConnectionClassManagerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var connectionClassManager: ConnectionClassManager

    @Before
    fun setup() {
        connectionClassManager = ConnectionClassManager(
            ExponentialGeometricAverage(),
            coroutineRule.testScope,
            ConnectionQualityStore(
                FakeSharedPreferencesProvider(),
                coroutineRule.testDispatcherProvider,
            ),
        )
    }

    @Test
    fun addLatencyThenGetLatency() = runTest {
        connectionClassManager.addLatency(100.0)

        assertEquals(100.0, connectionClassManager.getLatencyAverage(), 0.1)
    }

    @Test
    fun addLatencyThenGetConnectionQuality() = runTest {
        connectionClassManager.addLatency(0.0)
        assertEquals(ConnectionQuality.UNKNOWN, connectionClassManager.getConnectionQuality())
        connectionClassManager.reset()

        connectionClassManager.addLatency(0.1)
        assertEquals(ConnectionQuality.EXCELLENT, connectionClassManager.getConnectionQuality())
        connectionClassManager.reset()

        connectionClassManager.addLatency(20.0)
        assertEquals(ConnectionQuality.EXCELLENT, connectionClassManager.getConnectionQuality())
        connectionClassManager.reset()

        connectionClassManager.addLatency(21.0)
        assertEquals(ConnectionQuality.GOOD, connectionClassManager.getConnectionQuality())
        connectionClassManager.reset()

        connectionClassManager.addLatency(50.0)
        assertEquals(ConnectionQuality.GOOD, connectionClassManager.getConnectionQuality())
        connectionClassManager.reset()

        connectionClassManager.addLatency(51.0)
        assertEquals(ConnectionQuality.MODERATE, connectionClassManager.getConnectionQuality())
        connectionClassManager.reset()

        connectionClassManager.addLatency(200.0)
        assertEquals(ConnectionQuality.MODERATE, connectionClassManager.getConnectionQuality())
        connectionClassManager.reset()

        connectionClassManager.addLatency(201.0)
        assertEquals(ConnectionQuality.POOR, connectionClassManager.getConnectionQuality())
        connectionClassManager.reset()

        connectionClassManager.addLatency(300.0)
        assertEquals(ConnectionQuality.POOR, connectionClassManager.getConnectionQuality())
        connectionClassManager.reset()

        connectionClassManager.addLatency(301.0)
        assertEquals(ConnectionQuality.TERRIBLE, connectionClassManager.getConnectionQuality())
        connectionClassManager.reset()
    }

    @Test
    fun addLatencyThenCalculateRunningAverage() = runTest {
        val expected = ExponentialGeometricAverage()

        for (i in 0..2000 step 1) {
            connectionClassManager.addLatency(i.toDouble())
            expected.addMeasurement(i.toDouble())
            assertEquals(expected.average, connectionClassManager.getLatencyAverage(), 0.0)
            assertEquals(expected.average.asConnectionQuality(), connectionClassManager.getConnectionQuality())
        }
    }
}
