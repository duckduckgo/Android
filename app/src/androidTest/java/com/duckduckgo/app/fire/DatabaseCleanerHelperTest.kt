/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.fire

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class DatabaseCleanerHelperTest {

    @get:Rule
    private val coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var testee: DatabaseCleanerHelper
    private val databaseLocator = AuthDatabaseLocator(context)

    @Before
    fun before() {
        testee = DatabaseCleanerHelper(coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenCleanDatabaseThenReturnTrue() = runTest {
        assertTrue(testee.cleanDatabase(databaseLocator.getDatabasePath()))
    }

    @Test
    fun whenChangeJournalModeToDeleteThenReturnTrue() = runTest {
        assertTrue(testee.changeJournalModeToDelete(databaseLocator.getDatabasePath()))
    }
}
