/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.browser.rating.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.rating.PromptCount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
@Suppress("RemoveExplicitTypeArguments")
class AppEnjoymentDatabaseRepositoryTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: AppEnjoymentDatabaseRepository

    private lateinit var database: AppDatabase
    private lateinit var dao: AppEnjoymentDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        dao = database.appEnjoymentDao()
        testee = AppEnjoymentDatabaseRepository(dao)
    }

    @After
    fun after() {
        database.close()
    }

    @Test
    fun whenFirstCreatedThenPrompt1CanBeShown() = runTest {
        assertTrue(testee.canUserBeShownFirstPrompt())
    }

    @Test
    fun whenUserGaveFeedbackForPrompt1ThenPrompt1CannotBeShown() = runTest {
        testee.onUserSelectedToGiveFeedback(FIRST_PROMPT)
        assertFalse(testee.canUserBeShownFirstPrompt())
    }

    @Test
    fun whenUserDeclinedToGiveFeedbackForPrompt1ThenPrompt1CannotBeShown() = runTest {
        testee.onUserDeclinedToGiveFeedback(FIRST_PROMPT)
        assertFalse(testee.canUserBeShownFirstPrompt())
    }

    @Test
    fun whenUserGaveRatingForPrompt1ThenPrompt1CannotBeShown() = runTest {
        testee.onUserSelectedToRateApp(FIRST_PROMPT)
        assertFalse(testee.canUserBeShownFirstPrompt())
    }

    @Test
    fun whenUserDeclinedRatingForPrompt1ThenPrompt1CannotBeShown() = runTest {
        testee.onUserDeclinedToRateApp(FIRST_PROMPT)
        assertFalse(testee.canUserBeShownFirstPrompt())
    }

    @Test
    fun whenUserDeclinedToSayWhetherEnjoyingForPrompt1ThenPrompt1CannotBeShown() = runTest {
        testee.onUserDeclinedToSayIfEnjoyingApp(FIRST_PROMPT)
        assertFalse(testee.canUserBeShownFirstPrompt())
    }

    @Test
    fun whenUserGaveFeedbackForPrompt2ThenPrompt2CannotBeShownAgain() = runTest {
        testee.onUserSelectedToGiveFeedback(SECOND_PROMPT)
        assertFalse(testee.canUserBeShownSecondPrompt())
    }

    @Test
    fun whenUserDeclinedToGiveFeedbackForPrompt2ThenPrompt2CannotBeShownAgain() = runTest {
        testee.onUserDeclinedToGiveFeedback(SECOND_PROMPT)
        assertFalse(testee.canUserBeShownSecondPrompt())
    }

    @Test
    fun whenUserGaveRatingForPrompt2ThenPrompt2CannotBeShownAgain() = runTest {
        testee.onUserSelectedToRateApp(SECOND_PROMPT)
        assertFalse(testee.canUserBeShownSecondPrompt())
    }

    @Test
    fun whenUserDeclinedRatingForPrompt2ThenPrompt2CannotBeShownAgain() = runTest {
        testee.onUserDeclinedToRateApp(SECOND_PROMPT)
        assertFalse(testee.canUserBeShownSecondPrompt())
    }

    @Test
    fun whenUserDeclinedToSayWhetherEnjoyingForPrompt2ThenPrompt2CannotBeShownAgain() = runTest {
        testee.onUserDeclinedToSayIfEnjoyingApp(SECOND_PROMPT)
        assertFalse(testee.canUserBeShownSecondPrompt())
    }

    companion object {
        private val FIRST_PROMPT = PromptCount(1)
        private val SECOND_PROMPT = PromptCount(2)
    }
}
