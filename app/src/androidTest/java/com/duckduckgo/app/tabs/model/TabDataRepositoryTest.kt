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

package com.duckduckgo.app.tabs.model

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.support.test.annotation.UiThreadTest
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.tabs.db.TabsDao
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.verify
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class TabDataRepositoryTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Mock
    private lateinit var mockDao: TabsDao

    private lateinit var testee: TabDataRepository

    @UiThreadTest
    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        testee = TabDataRepository(mockDao)
    }

    @Test
    fun whenAddNewCalledTheTabAddedAndSelectedAndSiteDataCreated() {
        val createdId = testee.addNew()
        verify(mockDao).addAndSelectTab(any())
        assertNotNull(testee.retrieveSiteData(createdId))
    }

    @Test
    fun whenAddCalledThenSiteDataAdded() {
        val record = MutableLiveData<Site>()
        testee.add(TAB_ID, record)
        assertSame(record, testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenDataExistsForTabThenRetrieveReturnsIt() {
        val record = MutableLiveData<Site>()
        testee.add(TAB_ID, record)
        assertSame(record, testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenDataDoesNotExistForTabThenRetrieveCreatesIt() {
        assertNotNull(testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenIdSelectedThenCurrentUpdated() {
        testee.select(TAB_ID)
        verify(mockDao).insertTabSelection(eq(TabSelectionEntity(tabId = TAB_ID)))
    }

    companion object {
        const val TAB_ID = "abcd"
    }

}