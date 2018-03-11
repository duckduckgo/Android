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
import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabSelectionEntity
import com.duckduckgo.app.tabs.model.TabEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TabsDaoTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var testee: TabsDao

    @Before
    fun before() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testee = database.tabsDao()
    }

    @Test
    fun whenTabInsertedThenItIsInTabsList() {
        val entity = TabEntity("TAB_ID", "http://example.com", "Example")
        assertTrue(testee.tabs().isEmpty())
        testee.insertTab(entity)
        assertEquals(1, testee.tabs().count())
        assertTrue(testee.tabs().contains(entity))
    }

    @Test
    fun whenSelectedTabIsInsertedThenRecordIsUpdated() {
        val tab = TabEntity("TAB_ID", "http://example.com", "Example")
        val tabSelection = TabSelectionEntity(1, "TAB_ID")
        testee.insertTab(tab)
        testee.insertTabSelection(tabSelection)
        assertEquals(tab, testee.selectedTab())
    }

}