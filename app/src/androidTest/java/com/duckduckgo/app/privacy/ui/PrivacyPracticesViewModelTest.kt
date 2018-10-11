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

package com.duckduckgo.app.privacy.ui

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import android.net.Uri
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.*
import com.duckduckgo.app.privacy.model.TermsOfService
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
        assertEquals(UNKNOWN, viewState.practices)
        assertEquals(0, viewState.goodTerms.size)
        assertEquals(0, viewState.badTerms.size)
    }

    @Test
    fun whenUrlIsUpdatedThenViewModelDomainIsUpdated() {
        testee.onSiteChanged(site(url = "http://example.com/path"))
        assertEquals("example.com", testee.viewState.value!!.domain)
    }

    @Test
    fun whenTermsAreUpdatedThenViewModelPracticesAndTermsListsAreUpdated() {
        val terms = TermsOfService(classification = "C", goodPrivacyTerms = listOf("good", "good"), badPrivacyTerms = listOf("good"))
        testee.onSiteChanged(site(terms = terms))
        val viewState = testee.viewState.value!!
        assertEquals(POOR, viewState.practices)
        assertEquals(2, viewState.goodTerms.size)
        assertEquals(1, viewState.badTerms.size)
    }

    private fun site(url: String = "", terms: TermsOfService = TermsOfService()): Site {
        val site: Site = mock()
        whenever(site.url).thenReturn(url)
        whenever(site.uri).thenReturn(Uri.parse(url))
        whenever(site.termsOfService).thenReturn(terms)
        return site
    }

}