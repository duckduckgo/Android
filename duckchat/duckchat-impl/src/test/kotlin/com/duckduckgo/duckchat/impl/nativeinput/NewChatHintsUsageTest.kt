/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.nativeinput

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintsUsage.Companion.HIDE_AFTER_IGNORED_CHATS
import com.duckduckgo.duckchat.impl.nativeinput.footer.FailingPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NewChatHintsUsageTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var testee: NewChatHintsUsage

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("new_chat_hints_${UUID.randomUUID()}") },
        )
        testee = usage(dataStore)
    }

    @Test
    fun whenNoPromptsCountedThenNotExhausted() = runTest {
        assertFalse(testee.isExhausted.first())
    }

    @Test
    fun whenPromptedAfterSeeingHintsEnoughTimesThenExhausted() = runTest {
        repeat(HIDE_AFTER_IGNORED_CHATS - 1) { ignoreHints() }
        assertFalse(testee.isExhausted.first())

        ignoreHints()

        assertTrue(testee.isExhausted.first())
    }

    @Test
    fun whenHintsWereNeverDisplayedThenPromptDoesNotCount() = runTest {
        repeat(HIDE_AFTER_IGNORED_CHATS) { testee.onPromptSubmitted(TAB, isNewChat = true) }

        assertFalse(testee.isExhausted.first())
    }

    @Test
    fun whenSeveralPromptsFollowOneDisplayThenOnlyOneCounts() = runTest {
        repeat(HIDE_AFTER_IGNORED_CHATS - 1) { ignoreHints() }
        testee.onPromptSubmitted(TAB, isNewChat = true)

        assertFalse(testee.isExhausted.first())
    }

    @Test
    fun whenPromptedAfterATapThenTheCountStartsOver() = runTest {
        repeat(HIDE_AFTER_IGNORED_CHATS - 1) { ignoreHints() }

        testee.onHintsDisplayed(TAB)
        testee.onHintTapped(TAB)
        testee.onPromptSubmitted(TAB, isNewChat = true)
        repeat(HIDE_AFTER_IGNORED_CHATS - 1) { ignoreHints() }

        assertFalse(testee.isExhausted.first())
    }

    @Test
    fun whenTappedWithoutPromptingThenTheCountIsKept() = runTest {
        repeat(HIDE_AFTER_IGNORED_CHATS - 1) { ignoreHints() }

        testee.onHintsDisplayed(TAB)
        testee.onHintTapped(TAB)
        ignoreHints(tabId = OTHER_TAB)

        assertTrue(testee.isExhausted.first())
    }

    @Test
    fun whenHintsShowAgainAfterATapThenThePromptStillResets() = runTest {
        repeat(HIDE_AFTER_IGNORED_CHATS - 1) { ignoreHints() }

        testee.onHintsDisplayed(TAB)
        testee.onHintTapped(TAB)
        testee.onHintsDisplayed(TAB)
        testee.onPromptSubmitted(TAB, isNewChat = true)
        ignoreHints()

        assertFalse(testee.isExhausted.first())
    }

    @Test
    fun whenPromptedOutsideANewChatThenItDoesNotCountAndClearsTheDisplay() = runTest {
        repeat(HIDE_AFTER_IGNORED_CHATS - 1) { ignoreHints() }

        testee.onHintsDisplayed(TAB)
        testee.onPromptSubmitted(TAB, isNewChat = false)
        testee.onPromptSubmitted(TAB, isNewChat = true)

        assertFalse(testee.isExhausted.first())
    }

    @Test
    fun whenPromptedOutsideANewChatAfterATapThenTheCountIsKept() = runTest {
        repeat(HIDE_AFTER_IGNORED_CHATS - 1) { ignoreHints() }

        testee.onHintsDisplayed(TAB)
        testee.onHintTapped(TAB)
        testee.onPromptSubmitted(TAB, isNewChat = false)
        ignoreHints()

        assertTrue(testee.isExhausted.first())
    }

    @Test
    fun whenResetThenNotExhausted() = runTest {
        repeat(HIDE_AFTER_IGNORED_CHATS) { ignoreHints() }

        testee.reset()

        assertFalse(testee.isExhausted.first())
    }

    @Test
    fun whenDataStoreFailsThenNotExhaustedAndWritesDoNotThrow() = runTest {
        val failing = usage(FailingPreferencesDataStore())

        failing.onHintsDisplayed(TAB)
        failing.onPromptSubmitted(TAB, isNewChat = true)
        failing.onHintTapped(TAB)
        failing.onPromptSubmitted(TAB, isNewChat = true)
        failing.reset()

        assertFalse(failing.isExhausted.first())
    }

    private fun ignoreHints(tabId: String = TAB) {
        testee.onHintsDisplayed(tabId)
        testee.onPromptSubmitted(tabId, isNewChat = true)
    }

    private fun usage(dataStore: DataStore<Preferences>) =
        NewChatHintsUsage(dataStore, coroutineRule.testDispatcherProvider, coroutineRule.testScope)

    private companion object {
        const val TAB = "tab"
        const val OTHER_TAB = "other"
    }
}
