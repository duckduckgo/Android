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
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.nhaarman.mockitokotlin2.*
import io.reactivex.Observable
import okhttp3.ResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test


class StatisticsRequesterTest {

    private var mockStatisticsStore: StatisticsDataStore = mock()
    private var mockService: StatisticsService = mock()
    private var mockResponseBody: ResponseBody = mock()
    private var mockVariantManager: VariantManager = mock()

    private var testee: StatisticsRequester = StatisticsRequester(mockStatisticsStore, mockService, mockVariantManager)

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Before
    fun before() {
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("ma", 100.0, filterBy = { true }))
        whenever(mockService.atb(any())).thenReturn(Observable.just(ATB))
        whenever(mockService.updateSearchAtb(any(), any(), any())).thenReturn(Observable.just(Atb(NEW_ATB)))
        whenever(mockService.exti(any(), any())).thenReturn(Observable.just(mockResponseBody))
    }

    @Test
    fun whenUpdateVersionPresentDuringRefreshSearchRetentionThenPreviousAtbIsReplacedWithUpdateVersion() {
        configureStoredStatistics()
        whenever(mockService.updateSearchAtb(any(), any(), any())).thenReturn(Observable.just(UPDATE_ATB))
        testee.refreshSearchRetentionAtb()
        verify(mockStatisticsStore).atb = Atb(UPDATE_ATB.updateVersion!!)
    }

    @Test
    fun whenUpdateVersionPresentDuringRefreshAppRetentionThenPreviousAtbIsReplacedWithUpdateVersion() {
        configureStoredStatistics()
        whenever(mockService.updateAppAtb(any(), any(), any())).thenReturn(Observable.just(UPDATE_ATB))
        testee.refreshAppRetentionAtb()
        verify(mockStatisticsStore).atb = Atb(UPDATE_ATB.updateVersion!!)
    }

    @Test
    fun whenNoStatisticsStoredThenInitializeAtbInvokesExti() {
        configureNoStoredStatistics()
        testee.initializeAtb()
        verify(mockService).atb(any())
        verify(mockService).exti(eq(ATB_WITH_VARIANT), any())
        verify(mockStatisticsStore).saveAtb(ATB)
    }

    @Test
    fun whenStatisticsStoredThenInitializeAtbDoesNothing() {
        configureStoredStatistics()
        testee.initializeAtb()
        verify(mockService, never()).atb(any())
        verify(mockService, never()).exti(eq(ATB.version), any())
    }

    @Test
    fun whenNoStatisticsStoredThenRefreshSearchRetentionRetrievesAtbAndInvokesExti() {
        configureNoStoredStatistics()
        testee.refreshSearchRetentionAtb()
        verify(mockService).atb(any())
        verify(mockService).exti(eq(ATB_WITH_VARIANT), any())
        verify(mockStatisticsStore).saveAtb(ATB)
    }

    @Test
    fun whenNoStatisticsStoredThenRefreshAppRetentionRetrievesAtbAndInvokesExti() {
        configureNoStoredStatistics()
        testee.refreshAppRetentionAtb()
        verify(mockService).atb(any())
        verify(mockService).exti(eq(ATB_WITH_VARIANT), any())
        verify(mockStatisticsStore).saveAtb(ATB)
    }

    @Test
    fun whenExtiFailsThenAtbCleared() {
        whenever(mockService.exti(any(), any())).thenReturn(Observable.error(Throwable()))
        configureNoStoredStatistics()
        testee.initializeAtb()
        verify(mockStatisticsStore).saveAtb(ATB)
        verify(mockStatisticsStore).clearAtb()
    }

    @Test
    fun whenStatisticsStoredThenRefreshIncludesRefreshedAtb() {
        configureStoredStatistics()
        val retentionAtb = "foo"
        whenever(mockStatisticsStore.searchRetentionAtb).thenReturn(retentionAtb)
        testee.refreshSearchRetentionAtb()
        verify(mockService).updateSearchAtb(eq(ATB_WITH_VARIANT), eq(retentionAtb), any())
    }

    @Test
    fun whenStatisticsStoredThenRefreshUpdatesAtb() {
        configureStoredStatistics()
        testee.refreshSearchRetentionAtb()
        verify(mockService).updateSearchAtb(eq(ATB_WITH_VARIANT), eq(ATB.version), any())
        verify(mockStatisticsStore).searchRetentionAtb = NEW_ATB
    }

    @Test
    fun whenAlreadyInitializedWithLegacyAtbThenInitializationRemovesLegacyVariant() {
        configureStoredStatistics()
        whenever(mockStatisticsStore.atb).thenReturn(Atb("v123ma"))
        testee.initializeAtb()
        verify(mockStatisticsStore).atb = Atb("v123")
        verify(mockStatisticsStore).variant = ""
    }

    private fun configureNoStoredStatistics() {
        whenever(mockStatisticsStore.hasInstallationStatistics).thenReturn(false)
        whenever(mockStatisticsStore.atb).thenReturn(null)
        whenever(mockStatisticsStore.searchRetentionAtb).thenReturn(null)
    }

    private fun configureStoredStatistics() {
        whenever(mockStatisticsStore.hasInstallationStatistics).thenReturn(true)
        whenever(mockStatisticsStore.atb).thenReturn(ATB)
        whenever(mockStatisticsStore.searchRetentionAtb).thenReturn(ATB.version)
    }

    companion object {
        private val ATB = Atb("v105-2")
        private val UPDATE_ATB = Atb("v105-2", updateVersion = "v99-1")
        private const val ATB_WITH_VARIANT = "v105-2ma"
        private const val NEW_ATB = "v105-4"
    }
}