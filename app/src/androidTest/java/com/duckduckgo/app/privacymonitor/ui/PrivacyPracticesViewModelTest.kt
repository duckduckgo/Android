/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacymonitor.ui

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import android.net.Uri
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test


class PrivacyPracticesViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private var viewStateObserver: Observer<PrivacyPracticesViewModel.ViewState> = mock()

    private val testee: PrivacyPracticesViewModel by lazy {
        val model = PrivacyPracticesViewModel()
        model.viewState.observeForever(viewStateObserver)
        model
    }

    @After
    fun after() {
        testee.viewState.removeObserver(viewStateObserver)
    }

    @Test
    fun whenNoDataThenDefaultValuesAreUsed() {
        val viewState = testee.viewState.value!!
        assertEquals("", viewState.domain)
        assertEquals(TermsOfService.Practices.UNKNOWN, viewState.practices)
        assertEquals(0, viewState.goodTerms.size)
        assertEquals(0, viewState.badTerms.size)
    }

    @Test
    fun whenUrlIsUpdatedThenViewModelDomainIsUpdated() {
        testee.onPrivacyMonitorChanged(monitor(url = "http://example.com/path"))
        assertEquals("example.com", testee.viewState.value!!.domain)
    }

    @Test
    fun whenTermsAreUpdatedThenViewModelPracticesAndTermsListsAresUpdated() {
        val terms = TermsOfService(classification = "C", goodPrivacyTerms = listOf("good", "good"), badPrivacyTerms = listOf("good"))
        testee.onPrivacyMonitorChanged(monitor(terms = terms))
        val viewState = testee.viewState.value!!
        assertEquals(TermsOfService.Practices.POOR, viewState.practices)
        assertEquals(2, viewState.goodTerms.size)
        assertEquals(1, viewState.badTerms.size)
    }

    private fun monitor(url: String = "", terms: TermsOfService = TermsOfService()): PrivacyMonitor {
        val monitor: PrivacyMonitor = mock()
        whenever(monitor.url).thenReturn(url)
        whenever(monitor.uri).thenReturn(Uri.parse(url))
        whenever(monitor.termsOfService).thenReturn(terms)
        return monitor
    }

}