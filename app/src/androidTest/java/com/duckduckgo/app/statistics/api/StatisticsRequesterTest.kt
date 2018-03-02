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

package com.duckduckgo.app.statistics.api

import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.nhaarman.mockito_kotlin.*
import io.reactivex.Observable
import okhttp3.ResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test


class StatisticsRequesterTest {

    private var mockStatisticsStore: StatisticsDataStore = mock()
    private var mockService: StatisticsService = mock()
    private var mockResponseBody: ResponseBody = mock()

    private var testee: StatisticsRequester = StatisticsRequester(mockStatisticsStore, mockService)

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Before
    fun before() {
        whenever(mockService.atb()).thenReturn(Observable.just(Atb(ATB)))
        whenever(mockService.updateAtb(any(), any())).thenReturn(Observable.just(Atb(NEW_ATB)))
        whenever(mockService.exti(any())).thenReturn(Observable.just(mockResponseBody))
    }

    @Test
    fun whenNoStatisticsStoredThenInitializeAtbRetrievesAtbAndInvokesExti() {
        configureNoStoredStatistics()
        testee.initializeAtb()
        verify(mockService).atb()
        verify(mockService).exti(ATB_WITH_VARIANT)
        verify(mockStatisticsStore).atb = ATB_WITH_VARIANT
        verify(mockStatisticsStore).retentionAtb = ATB
    }

    @Test
    fun whenStatisticsStoredThenInitializeAtbDoesNothing() {
        configureStoredStatistics()
        testee.initializeAtb()
        verify(mockService, never()).atb()
        verify(mockService, never()).exti(ATB)
    }

    @Test
    fun whenNoStatisticsStoredThenRefreshRetrievesAtbAndInvokesExti() {
        configureNoStoredStatistics()
        testee.refreshRetentionAtb()
        verify(mockService).atb()
        verify(mockService).exti(ATB_WITH_VARIANT)
        verify(mockStatisticsStore).atb = ATB_WITH_VARIANT
        verify(mockStatisticsStore).retentionAtb = ATB
    }

    @Test
    fun whenExitFailsThenAtbCleared() {
        whenever(mockService.exti(any())).thenReturn(Observable.error(Throwable()))
        configureNoStoredStatistics()
        testee.initializeAtb()
        verify(mockStatisticsStore).atb = ATB_WITH_VARIANT
        verify(mockStatisticsStore).retentionAtb = ATB
        verify(mockStatisticsStore).atb = null
        verify(mockStatisticsStore).retentionAtb = null
    }

    @Test
    fun whenStatisticsStoredThenRefreshUpdatesAtb() {
        configureStoredStatistics()
        testee.refreshRetentionAtb()
        verify(mockService).updateAtb(ATB_WITH_VARIANT, ATB)
        verify(mockStatisticsStore).retentionAtb = NEW_ATB
    }

    private fun configureNoStoredStatistics() {
        whenever(mockStatisticsStore.hasInstallationStatistics).thenReturn(false)
        whenever(mockStatisticsStore.atb).thenReturn(null)
        whenever(mockStatisticsStore.retentionAtb).thenReturn(null)
    }

    private fun configureStoredStatistics() {
        whenever(mockStatisticsStore.hasInstallationStatistics).thenReturn(true)
        whenever(mockStatisticsStore.atb).thenReturn(ATB_WITH_VARIANT)
        whenever(mockStatisticsStore.retentionAtb).thenReturn(ATB)
    }

    companion object {
        private const val ATB = "v105-2"
        private const val ATB_WITH_VARIANT = "v105-2ma"
        private const val NEW_ATB = "v105-4"
    }
}