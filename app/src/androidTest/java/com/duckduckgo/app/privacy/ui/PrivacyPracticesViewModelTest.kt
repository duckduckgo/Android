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

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.POOR
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.UNKNOWN
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
@ExperimentalTime
class PrivacyPracticesViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: PrivacyPracticesViewModel

    @Before
    fun before() {
        testee = PrivacyPracticesViewModel()
    }

    @Test
    fun whenNoDataThenDefaultValuesAreUsed() = coroutineTestRule.runBlocking {
        testee.viewState().test {
            val viewState = awaitItem()
            assertEquals("", viewState.domain)
            assertEquals(UNKNOWN, viewState.practices)
            assertEquals(0, viewState.goodTerms.size)
            assertEquals(0, viewState.badTerms.size)
        }
    }

    @Test
    fun whenUrlIsUpdatedThenViewModelDomainIsUpdated() = coroutineTestRule.runBlocking {
        testee.onSiteChanged(site(url = "http://example.com/path"))
        testee.viewState().test {
            assertEquals("example.com", awaitItem().domain)
        }
    }

    @Test
    fun whenPrivacyPracticesAreUpdatedThenViewModelPracticesAndTermsListsAreUpdated() = coroutineTestRule.runBlocking {
        val privacyPractices = PrivacyPractices.Practices(0, POOR, listOf("good", "also good"), listOf("bad"))

        testee.onSiteChanged(site(privacyPractices = privacyPractices))
        testee.viewState().test {
            val viewState = awaitItem()
            assertEquals(POOR, viewState.practices)
            assertEquals(2, viewState.goodTerms.size)
            assertEquals(1, viewState.badTerms.size)
        }
    }

    private fun site(url: String = "", privacyPractices: PrivacyPractices.Practices = PrivacyPractices.UNKNOWN): Site {
        val site: Site = mock()
        whenever(site.url).thenReturn(url)
        whenever(site.uri).thenReturn(Uri.parse(url))
        whenever(site.privacyPractices).thenReturn(privacyPractices)
        return site
    }

}
