/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.integration

import android.support.test.InstrumentationRegistry
import android.support.test.filters.LargeTest
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.getDaggerComponent
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.api.StatisticsRequester
import com.duckduckgo.app.statistics.api.StatisticsService
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.statistics.store.StatisticsSharedPreferences
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * These tests communicate with the server and therefore will be slowed down if there is slow internet access.
 * Additionally, this makes them susceptible to failing if the device running tests has no internet access.
 *
 * Would normally have separate tests for each assertion, but these tests are relatively expensive to run.
 */
@LargeTest
class AtbIntegrationTest {

    private lateinit var mockVariantManager: VariantManager
    private lateinit var service: StatisticsService
    private lateinit var testee: StatisticsRequester
    private lateinit var statisticsStore: StatisticsDataStore

    @get:Rule
    val schedulers = InstantSchedulersRule()

    @Before
    fun before() {
        mockVariantManager = mock()
        statisticsStore = StatisticsSharedPreferences(InstrumentationRegistry.getTargetContext())
        statisticsStore.clearAtb()

        whenever(mockVariantManager.getVariant()).thenReturn(Variant("ma", 100.0))
        service = getDaggerComponent().retrofit().create(StatisticsService::class.java)
        testee = StatisticsRequester(statisticsStore, service, mockVariantManager)
    }

    @Test
    fun whenNoStatisticsStoredThenAtbInitializationSuccessfullyStoresAtbAndRetentionAtb() {
        testee.initializeAtb()
        assertTrue(statisticsStore.hasInstallationStatistics)
        val atb = statisticsStore.atb
        val retentionAtb = statisticsStore.retentionAtb
        assertNotNull(atb)
        assertNotNull(retentionAtb)
        assertEquals(atb?.version, retentionAtb)
        assertAtbExpectedFormatted(atb!!.version)
    }

    @Test
    fun whenStatisticsAlreadyStoredThenRefreshSuccessfullyUpdatesRetentionAtbOnly() {
        statisticsStore.saveAtb(Atb("v100-1"))
        testee.refreshRetentionAtb()
        assertTrue(statisticsStore.hasInstallationStatistics)
        val atb = statisticsStore.atb
        val retentionAtb = statisticsStore.retentionAtb
        assertNotNull(atb)
        assertNotNull(retentionAtb)

        assertEquals("v100-1", atb!!.version)
        assertNotEquals(atb.version, retentionAtb)
        assertAtbExpectedFormatted(atb.version)
        assertAtbExpectedFormatted(retentionAtb!!)
    }

    private fun assertAtbExpectedFormatted(atb: String) {
        assertTrue(atb.startsWith("v"))
        assertTrue(atb.contains("-"))

        val split = atb.substring(1).split("-")
        assertEquals(2, split.size)

        assertAtbWeekExpectedFormat(split[0].toInt())
        assertAtbDayExpectedFormat(split[1].toInt())
    }

    private fun assertAtbWeekExpectedFormat(week: Int) {
        assertTrue(week > 0)
    }

    private fun assertAtbDayExpectedFormat(day: Int) {
        assertTrue(day >= 1)
        assertTrue(day <= 7)
    }
}