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

package com.duckduckgo.app.tabs

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.support.test.annotation.UiThreadTest
import com.duckduckgo.app.global.model.Site
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TabDataRepositoryTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testee: TabDataRepository

    @UiThreadTest
    @Before
    fun before() {
        testee = TabDataRepository()
    }

    @Test
    fun whenInitializedThenTabsModelIsNonNullTabsIsFalseAndCurrentIsNull() {
        assertNotNull(testee.tabs)
        assertFalse(testee.hasTabs())
        assertNull(testee.currentTabId)
    }

    @Test
    fun whenRecordAddedThenRecordExists() {
        val record = MutableLiveData<Site>()
        testee.add(TAB_ID, record)
        assertSame(record, testee.retrieve(TAB_ID))
    }

    @Test
    fun whenIdExistsThenRetrieveReturnsIt() {
        val record = MutableLiveData<Site>()
        testee.add(TAB_ID, record)
        assertSame(record, testee.retrieve(TAB_ID))
    }

    @Test
    fun whenIdDoesNotExistThenRetrieveCreatesIt() {
        assertNotNull(testee.retrieve(TAB_ID))
    }

    @Test
    fun whenIdSelectedThenCurrentUpdated() {
        testee.select(TAB_ID)
        assertEquals(TAB_ID, testee.currentTabId)
    }

    companion object {
        const val TAB_ID = "abcdefg"
    }

}