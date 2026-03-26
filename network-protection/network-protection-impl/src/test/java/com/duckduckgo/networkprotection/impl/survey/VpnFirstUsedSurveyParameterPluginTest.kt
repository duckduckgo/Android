package com.duckduckgo.networkprotection.impl.survey

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.networkprotection.impl.cohort.NetpCohortStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalDateTime

class VpnFirstUsedSurveyParameterPluginTest {
    @Mock
    private lateinit var netpCohortStore: NetpCohortStore

    @Mock
    private lateinit var currentTimeProvider: CurrentTimeProvider

    private lateinit var plugin: VpnFirstUsedSurveyParameterPlugin

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        plugin = VpnFirstUsedSurveyParameterPlugin(netpCohortStore, currentTimeProvider)
    }

    @Test
    fun whenCohortSetThenDaysSinceVpnFirstUsedParamEvaluatesToData() = runTest {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(LocalDate.of(2024, 6, 8))
        whenever(currentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.of(2024, 6, 18, 0, 0))

        assertEquals("10", plugin.evaluate("vpn_first_used"))
    }

    @Test
    fun whenSubscriptionIsNotAvailableThenDaysUntilExpiryParamEvaluatesToZero() = runTest {
        whenever(netpCohortStore.cohortLocalDate).thenReturn(null)
        whenever(currentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.of(2024, 6, 18, 0, 0))

        assertEquals("0", plugin.evaluate("vpn_first_used"))
    }
}
