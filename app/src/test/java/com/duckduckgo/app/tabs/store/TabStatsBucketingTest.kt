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
    fun testGetNumberOfOpenTabsExactly1() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(1)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("1", result)
    }

    @Test
    fun testGetNumberOfOpenTabsZero() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(0)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("1", result)
    }

    @Test
    fun testGetNumberOfOpenTabs() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(5)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("2-5", result)
    }

    @Test
    fun testGetNumberOfOpenTabs6To10() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(8)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("6-10", result)
    }

    @Test
    fun testGetNumberOfOpenTabs11To20() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(11)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("11-20", result)
    }

    @Test
    fun testGetNumberOfOpenTabsMoreThan20() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(40)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("21-40", result)
    }

    @Test
    fun testGetNumberOfOpenTabs41To60() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(50)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("41-60", result)
    }

    @Test
    fun testGetNumberOfOpenTabs61To80() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(70)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("61-80", result)
    }

    @Test
    fun testGetNumberOfOpenTabs81To100() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(90)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("81-100", result)
    }

    @Test
    fun testGetNumberOfOpenTabs101To125() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(110)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("101-125", result)
    }

    @Test
    fun testGetNumberOfOpenTabs126To150() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(130)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("126-150", result)
    }

    @Test
    fun testGetNumberOfOpenTabs151To250() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(200)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("151-250", result)
    }

    @Test
    fun testGetNumberOfOpenTabs251To500() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(300)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("251-500", result)
    }

    @Test
    fun testGetNumberOfOpenTabsMaxValue() = runBlocking {
        whenever(tabRepository.getOpenTabCount()).thenReturn(600)
        val result = defaultTabStatsBucketing.getNumberOfOpenTabs()
        assertEquals("501+", result)
    }

    @Test
    fun testGetTabsActiveLastWeekZero() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(0, TabStatsBucketing.ONE_WEEK_IN_DAYS)).thenReturn(0)
        val result = defaultTabStatsBucketing.getTabsActiveLastWeek()
        assertEquals("0", result)
    }

    @Test
    fun testGetTabsActiveLastWeekExactly1() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(0, TabStatsBucketing.ONE_WEEK_IN_DAYS)).thenReturn(1)
        val result = defaultTabStatsBucketing.getTabsActiveLastWeek()
        assertEquals("1-5", result)
    }

    @Test
    fun testGetTabsActiveLastWeek1To5() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(0, TabStatsBucketing.ONE_WEEK_IN_DAYS)).thenReturn(2)
        val result = defaultTabStatsBucketing.getTabsActiveLastWeek()
        assertEquals("1-5", result)
    }

    @Test
    fun testGetTabsActiveLastWeek6To10() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(0, TabStatsBucketing.ONE_WEEK_IN_DAYS)).thenReturn(10)
        val result = defaultTabStatsBucketing.getTabsActiveLastWeek()
        assertEquals("6-10", result)
    }

    @Test
    fun testGetTabsActiveLastWeek11To20() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(0, TabStatsBucketing.ONE_WEEK_IN_DAYS)).thenReturn(15)
        val result = defaultTabStatsBucketing.getTabsActiveLastWeek()
        assertEquals("11-20", result)
    }

    @Test
    fun testGetTabsActiveLastWeekMoreThan20() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(0, TabStatsBucketing.ONE_WEEK_IN_DAYS)).thenReturn(25)
        val result = defaultTabStatsBucketing.getTabsActiveLastWeek()
        assertEquals("21+", result)
    }

    @Test
    fun testGetTabsActiveLastWeekALotMoreThan20() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(0, TabStatsBucketing.ONE_WEEK_IN_DAYS)).thenReturn(250)
        val result = defaultTabStatsBucketing.getTabsActiveLastWeek()
        assertEquals("21+", result)
    }

    @Test
    fun testGetTabsActiveOneWeekAgoZero() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.ONE_WEEK_IN_DAYS, TabStatsBucketing.TWO_WEEKS_IN_DAYS)).thenReturn(0)
        val result = defaultTabStatsBucketing.getTabsActiveOneWeekAgo()
        assertEquals("0", result)
    }

    @Test
    fun testGet1WeeksInactiveTabBucketExactly1() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.ONE_WEEK_IN_DAYS, TabStatsBucketing.TWO_WEEKS_IN_DAYS)).thenReturn(1)
        val result = defaultTabStatsBucketing.getTabsActiveOneWeekAgo()
        assertEquals("1-5", result)
    }

    @Test
    fun testGet1WeeksInactiveTabBucket1To5() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.ONE_WEEK_IN_DAYS, TabStatsBucketing.TWO_WEEKS_IN_DAYS)).thenReturn(3)
        val result = defaultTabStatsBucketing.getTabsActiveOneWeekAgo()
        assertEquals("1-5", result)
    }

    @Test
    fun testGetTabsActiveOneWeekAgo6To10() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.ONE_WEEK_IN_DAYS, TabStatsBucketing.TWO_WEEKS_IN_DAYS)).thenReturn(8)
        val result = defaultTabStatsBucketing.getTabsActiveOneWeekAgo()
        assertEquals("6-10", result)
    }

    @Test
    fun testGetTabsActiveOneWeekAgo11To20() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.ONE_WEEK_IN_DAYS, TabStatsBucketing.TWO_WEEKS_IN_DAYS)).thenReturn(15)
        val result = defaultTabStatsBucketing.getTabsActiveOneWeekAgo()
        assertEquals("11-20", result)
    }

    @Test
    fun testGetTabsActiveOneWeekAgoMoreThan20() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.ONE_WEEK_IN_DAYS, TabStatsBucketing.TWO_WEEKS_IN_DAYS)).thenReturn(25)
        val result = defaultTabStatsBucketing.getTabsActiveOneWeekAgo()
        assertEquals("21+", result)
    }

    @Test
    fun testGetTabsActiveTwoWeeksAgoZero() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.TWO_WEEKS_IN_DAYS, TabStatsBucketing.THREE_WEEKS_IN_DAYS)).thenReturn(0)
        val result = defaultTabStatsBucketing.getTabsActiveTwoWeeksAgo()
        assertEquals("0", result)
    }

    @Test
    fun testGetTabsActiveTwoWeeksAgoExactly1() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.TWO_WEEKS_IN_DAYS, TabStatsBucketing.THREE_WEEKS_IN_DAYS)).thenReturn(1)
        val result = defaultTabStatsBucketing.getTabsActiveTwoWeeksAgo()
        assertEquals("1-5", result)
    }

    @Test
    fun testGetTabsActiveTwoWeeksAgo1To5() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.TWO_WEEKS_IN_DAYS, TabStatsBucketing.THREE_WEEKS_IN_DAYS)).thenReturn(5)
        val result = defaultTabStatsBucketing.getTabsActiveTwoWeeksAgo()
        assertEquals("1-5", result)
    }

    @Test
    fun testGetTabsActiveTwoWeeksAgo6To10() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.TWO_WEEKS_IN_DAYS, TabStatsBucketing.THREE_WEEKS_IN_DAYS)).thenReturn(6)
        val result = defaultTabStatsBucketing.getTabsActiveTwoWeeksAgo()
        assertEquals("6-10", result)
    }

    @Test
    fun testGetTabsActiveTwoWeeksAgo11To20() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.TWO_WEEKS_IN_DAYS, TabStatsBucketing.THREE_WEEKS_IN_DAYS)).thenReturn(
            20,
        )
        val result = defaultTabStatsBucketing.getTabsActiveTwoWeeksAgo()
        assertEquals("11-20", result)
    }

    @Test
    fun testGetTabsActiveTwoWeeksAgoMoreThan20() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.TWO_WEEKS_IN_DAYS, TabStatsBucketing.THREE_WEEKS_IN_DAYS)).thenReturn(
            199,
        )
        val result = defaultTabStatsBucketing.getTabsActiveTwoWeeksAgo()
        assertEquals("21+", result)
    }

    @Test
    fun testGetTabsActiveMoreThanThreeWeeksAgoZero() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.THREE_WEEKS_IN_DAYS)).thenReturn(0)
        val result = defaultTabStatsBucketing.getTabsActiveMoreThanThreeWeeksAgo()
        assertEquals("0", result)
    }

    @Test
    fun testGetTabsActiveMoreThanThreeWeeksAgoExactly1() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.THREE_WEEKS_IN_DAYS)).thenReturn(1)
        val result = defaultTabStatsBucketing.getTabsActiveMoreThanThreeWeeksAgo()
        assertEquals("1-5", result)
    }

    @Test
    fun testGetTabsActiveMoreThanThreeWeeksAgo1To5() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.THREE_WEEKS_IN_DAYS)).thenReturn(5)
        val result = defaultTabStatsBucketing.getTabsActiveMoreThanThreeWeeksAgo()
        assertEquals("1-5", result)
    }

    @Test
    fun testGetTabsActiveMoreThanThreeWeeksAgo6To10() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.THREE_WEEKS_IN_DAYS)).thenReturn(10)
        val result = defaultTabStatsBucketing.getTabsActiveMoreThanThreeWeeksAgo()
        assertEquals("6-10", result)
    }

    @Test
    fun testGetTabsActiveMoreThanThreeWeeksAgo11To20() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.THREE_WEEKS_IN_DAYS)).thenReturn(11)
        val result = defaultTabStatsBucketing.getTabsActiveMoreThanThreeWeeksAgo()
        assertEquals("11-20", result)
    }

    @Test
    fun testGetTabsActiveMoreThanThreeWeeksAgoMoreThan20() = runBlocking {
        whenever(tabRepository.countTabsAccessedWithinRange(TabStatsBucketing.THREE_WEEKS_IN_DAYS)).thenReturn(21)
        val result = defaultTabStatsBucketing.getTabsActiveMoreThanThreeWeeksAgo()
        assertEquals("21+", result)
    }
}
