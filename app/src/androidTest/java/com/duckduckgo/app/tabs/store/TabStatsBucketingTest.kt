package com.duckduckgo.app.tabs.store
/*
 * Copyright (c) 2024 DuckDuckGo
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

import com.duckduckgo.app.tabs.model.TabRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class DefaultTabStatsBucketingTest {

    private val tabRepository = mock<TabRepository>()

    private lateinit var defaultTabStatsBucketing: DefaultTabStatsBucketing

    @Before
    fun setup() {
        defaultTabStatsBucketing = DefaultTabStatsBucketing(tabRepository)
    }

    @Test
    fun testGetTabCountBucketExactly1() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(1)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("1", result)
    }

    @Test
    fun testGetTabCountBucketZero() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(0)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("1", result)
    }

    @Test
    fun testGetTabCountBucket() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(5)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("2-5", result)
    }

    @Test
    fun testGetTabCountBucket6To10() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(8)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("6-10", result)
    }

    @Test
    fun testGetTabCountBucket11To20() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(11)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("11-20", result)
    }

    @Test
    fun testGetTabCountBucketMoreThan20() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(40)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("21-40", result)
    }

    @Test
    fun testGetTabCountBucket41To60() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(50)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("41-60", result)
    }

    @Test
    fun testGetTabCountBucket61To80() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(70)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("61-80", result)
    }

    @Test
    fun testGetTabCountBucket81To100() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(90)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("81-100", result)
    }

    @Test
    fun testGetTabCountBucket101To125() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(110)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("101-125", result)
    }

    @Test
    fun testGetTabCountBucket126To150() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(130)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("126-150", result)
    }

    @Test
    fun testGetTabCountBucket151To250() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(200)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("151-250", result)
    }

    @Test
    fun testGetTabCountBucket251To500() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(300)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("251-500", result)
    }

    @Test
    fun testGetTabCountBucketMaxValue() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(600)
        val result = defaultTabStatsBucketing.getTabCountBucket()
        assertEquals("501+", result)
    }

    @Test
    fun testGet7DaysActiveTabBucketZero() = runBlocking {
        whenever(tabRepository.getActiveTabCount(TabStatsBucketing.ACTIVE_TABS_DAYS_LIMIT)).thenReturn(0)
        val result = defaultTabStatsBucketing.get7DaysActiveTabBucket()
        assertEquals("0-1", result)
    }

    @Test
    fun testGet7DaysActiveTabBucketExactly1() = runBlocking {
        whenever(tabRepository.getActiveTabCount(TabStatsBucketing.ACTIVE_TABS_DAYS_LIMIT)).thenReturn(1)
        val result = defaultTabStatsBucketing.get7DaysActiveTabBucket()
        assertEquals("0-1", result)
    }

    @Test
    fun testGet7DaysActiveTabBucket2To5() = runBlocking {
        whenever(tabRepository.getActiveTabCount(TabStatsBucketing.ACTIVE_TABS_DAYS_LIMIT)).thenReturn(2)
        val result = defaultTabStatsBucketing.get7DaysActiveTabBucket()
        assertEquals("2-5", result)
    }

    @Test
    fun testGet7DaysActiveTabBucket6To10() = runBlocking {
        whenever(tabRepository.getActiveTabCount(TabStatsBucketing.ACTIVE_TABS_DAYS_LIMIT)).thenReturn(10)
        val result = defaultTabStatsBucketing.get7DaysActiveTabBucket()
        assertEquals("6-10", result)
    }

    @Test
    fun testGet7DaysActiveTabBucket11To20() = runBlocking {
        whenever(tabRepository.getActiveTabCount(TabStatsBucketing.ACTIVE_TABS_DAYS_LIMIT)).thenReturn(15)
        val result = defaultTabStatsBucketing.get7DaysActiveTabBucket()
        assertEquals("11-20", result)
    }

    @Test
    fun testGet7DaysActiveTabBucketMoreThan20() = runBlocking {
        whenever(tabRepository.getActiveTabCount(TabStatsBucketing.ACTIVE_TABS_DAYS_LIMIT)).thenReturn(25)
        val result = defaultTabStatsBucketing.get7DaysActiveTabBucket()
        assertEquals(">20", result)
    }

    @Test
    fun testGet7DaysActiveTabBucketALotMoreThan20() = runBlocking {
        whenever(tabRepository.getActiveTabCount(TabStatsBucketing.ACTIVE_TABS_DAYS_LIMIT)).thenReturn(250)
        val result = defaultTabStatsBucketing.get7DaysActiveTabBucket()
        assertEquals(">20", result)
    }

    @Test
    fun testGet1WeeksInactiveTabBucketZero() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.ONE_WEEK_INACTIVE_LIMIT.first, TabStatsBucketing.ONE_WEEK_INACTIVE_LIMIT.last)).thenReturn(0)
        val result = defaultTabStatsBucketing.get1WeeksInactiveTabBucket()
        assertEquals("0-1", result)
    }

    @Test
    fun testGet1WeeksInactiveTabBucketExactly1() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.ONE_WEEK_INACTIVE_LIMIT.first, TabStatsBucketing.ONE_WEEK_INACTIVE_LIMIT.last)).thenReturn(1)
        val result = defaultTabStatsBucketing.get1WeeksInactiveTabBucket()
        assertEquals("0-1", result)
    }

    @Test
    fun testGet1WeeksInactiveTabBucket2To5() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.ONE_WEEK_INACTIVE_LIMIT.first, TabStatsBucketing.ONE_WEEK_INACTIVE_LIMIT.last)).thenReturn(3)
        val result = defaultTabStatsBucketing.get1WeeksInactiveTabBucket()
        assertEquals("2-5", result)
    }

    @Test
    fun testGet1WeeksInactiveTabBucket6To10() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.ONE_WEEK_INACTIVE_LIMIT.first, TabStatsBucketing.ONE_WEEK_INACTIVE_LIMIT.last)).thenReturn(8)
        val result = defaultTabStatsBucketing.get1WeeksInactiveTabBucket()
        assertEquals("6-10", result)
    }

    @Test
    fun testGet1WeeksInactiveTabBucket11To20() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.ONE_WEEK_INACTIVE_LIMIT.first, TabStatsBucketing.ONE_WEEK_INACTIVE_LIMIT.last)).thenReturn(15)
        val result = defaultTabStatsBucketing.get1WeeksInactiveTabBucket()
        assertEquals("11-20", result)
    }

    @Test
    fun testGet1WeeksInactiveTabBucketMoreThan20() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.ONE_WEEK_INACTIVE_LIMIT.first, TabStatsBucketing.ONE_WEEK_INACTIVE_LIMIT.last)).thenReturn(25)
        val result = defaultTabStatsBucketing.get1WeeksInactiveTabBucket()
        assertEquals(">20", result)
    }

    @Test
    fun testGet2WeeksInactiveTabBucketZero() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.TWO_WEEKS_INACTIVE_LIMIT.first, TabStatsBucketing.TWO_WEEKS_INACTIVE_LIMIT.last)).thenReturn(0)
        val result = defaultTabStatsBucketing.get2WeeksInactiveTabBucket()
        assertEquals("0-1", result)
    }

    @Test
    fun testGet2WeeksInactiveTabBucketExactly1() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.TWO_WEEKS_INACTIVE_LIMIT.first, TabStatsBucketing.TWO_WEEKS_INACTIVE_LIMIT.last)).thenReturn(1)
        val result = defaultTabStatsBucketing.get2WeeksInactiveTabBucket()
        assertEquals("0-1", result)
    }

    @Test
    fun testGet2WeeksInactiveTabBucket2To5() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.TWO_WEEKS_INACTIVE_LIMIT.first, TabStatsBucketing.TWO_WEEKS_INACTIVE_LIMIT.last)).thenReturn(5)
        val result = defaultTabStatsBucketing.get2WeeksInactiveTabBucket()
        assertEquals("2-5", result)
    }

    @Test
    fun testGet2WeeksInactiveTabBucket6To10() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.TWO_WEEKS_INACTIVE_LIMIT.first, TabStatsBucketing.TWO_WEEKS_INACTIVE_LIMIT.last)).thenReturn(6)
        val result = defaultTabStatsBucketing.get2WeeksInactiveTabBucket()
        assertEquals("6-10", result)
    }

    @Test
    fun testGet2WeeksInactiveTabBucket11To20() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.TWO_WEEKS_INACTIVE_LIMIT.first, TabStatsBucketing.TWO_WEEKS_INACTIVE_LIMIT.last)).thenReturn(20)
        val result = defaultTabStatsBucketing.get2WeeksInactiveTabBucket()
        assertEquals("11-20", result)
    }

    @Test
    fun testGet2WeeksInactiveTabBucketMoreThan20() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.TWO_WEEKS_INACTIVE_LIMIT.first, TabStatsBucketing.TWO_WEEKS_INACTIVE_LIMIT.last)).thenReturn(199)
        val result = defaultTabStatsBucketing.get2WeeksInactiveTabBucket()
        assertEquals(">20", result)
    }

    @Test
    fun testGet3WeeksInactiveTabBucketZero() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.THREE_WEEKS_INACTIVE_LIMIT)).thenReturn(0)
        val result = defaultTabStatsBucketing.get3WeeksInactiveTabBucket()
        assertEquals("0-1", result)
    }

    @Test
    fun testGet3WeeksInactiveTabBucketExactly1() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.THREE_WEEKS_INACTIVE_LIMIT)).thenReturn(1)
        val result = defaultTabStatsBucketing.get3WeeksInactiveTabBucket()
        assertEquals("0-1", result)
    }

    @Test
    fun testGet3WeeksInactiveTabBucket2To5() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.THREE_WEEKS_INACTIVE_LIMIT)).thenReturn(5)
        val result = defaultTabStatsBucketing.get3WeeksInactiveTabBucket()
        assertEquals("2-5", result)
    }

    @Test
    fun testGet3WeeksInactiveTabBucket6To10() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.THREE_WEEKS_INACTIVE_LIMIT)).thenReturn(10)
        val result = defaultTabStatsBucketing.get3WeeksInactiveTabBucket()
        assertEquals("6-10", result)
    }

    @Test
    fun testGet3WeeksInactiveTabBucket11To20() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.THREE_WEEKS_INACTIVE_LIMIT)).thenReturn(11)
        val result = defaultTabStatsBucketing.get3WeeksInactiveTabBucket()
        assertEquals("11-20", result)
    }

    @Test
    fun testGet3WeeksInactiveTabBucketMoreThan20() = runBlocking {
        whenever(tabRepository.getInactiveTabCount(TabStatsBucketing.THREE_WEEKS_INACTIVE_LIMIT)).thenReturn(21)
        val result = defaultTabStatsBucketing.get3WeeksInactiveTabBucket()
        assertEquals(">20", result)
    }
}
