package com.duckduckgo.app.pixels.ppropromo.params

import com.duckduckgo.app.statistics.store.StatisticsDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class StatisticsAdditionalPixelParamPluginTest {
    @Test
    fun whenRuVariantSetThenPluginShouldReturnParamTrue() = runTest {
        val statisticsDataStore: StatisticsDataStore = mock()
        whenever(statisticsDataStore.variant).thenReturn("ru")
        val plugin = ReinstallAdditionalPixelParamPlugin(statisticsDataStore)

        Assert.assertEquals("isReinstall" to "true", plugin.params())
    }

    @Test
    fun whenVariantIsNotRuThenPluginShouldReturnParamFalse() = runTest {
        val statisticsDataStore: StatisticsDataStore = mock()
        whenever(statisticsDataStore.variant).thenReturn("atb-1234")
        val plugin = ReinstallAdditionalPixelParamPlugin(statisticsDataStore)

        Assert.assertEquals("isReinstall" to "false", plugin.params())
    }
}
