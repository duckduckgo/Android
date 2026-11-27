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

package com.duckduckgo.pagetranslation.impl.session

import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.session.TranslationState.Idle
import com.duckduckgo.pagetranslation.impl.session.TranslationState.Translated
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealTabTranslationTest {
    private val sessionFactory = FakeTranslationSessionFactory()
    private val tabTranslation = RealTabTranslation(sessionFactory)

    @Test
    fun startASessionForTheDocument() {
        tabTranslation.startSession(TIME_ORIGIN)

        assertEquals(listOf(TIME_ORIGIN), sessionFactory.sessions.map { it.timeOrigin })
    }

    @Test
    fun endThePreviousSessionWhenANewOneStarts() {
        tabTranslation.startSession(TIME_ORIGIN)
        tabTranslation.translate(PAIR)

        tabTranslation.startSession(TIME_ORIGIN + 1)

        assertEquals(listOf(true, false), sessionFactory.sessions.map { it.isEnded })
        assertEquals(Idle, tabTranslation.state.value)
    }

    @Test
    fun endTheSessionAndResetTheStateToIdle() {
        tabTranslation.startSession(TIME_ORIGIN)
        tabTranslation.translate(PAIR)

        tabTranslation.endSession()

        assertTrue(sessionFactory.sessions.single().isEnded)
        assertEquals(Idle, tabTranslation.state.value)
    }

    @Test
    fun translateTheCurrentDocument() {
        tabTranslation.startSession(TIME_ORIGIN)
        tabTranslation.startSession(TIME_ORIGIN + 1)

        tabTranslation.translate(PAIR)

        assertEquals(listOf(emptyList(), listOf(PAIR)), sessionFactory.sessions.map { it.translated })
        assertEquals(Translated, tabTranslation.state.value)
    }

    @Test
    fun doNothingWithoutASession() {
        tabTranslation.translate(PAIR)
        tabTranslation.startSession(TIME_ORIGIN)

        assertEquals(emptyList<LanguagePair>(), sessionFactory.sessions.single().translated)
        assertEquals(Idle, tabTranslation.state.value)
    }

    @Test
    fun tellTheSessionWhetherTheTabIsVisible() {
        tabTranslation.setVisible(true)
        tabTranslation.startSession(TIME_ORIGIN)

        assertTrue(sessionFactory.sessions.single().isVisible.value)
        tabTranslation.setVisible(false)
        assertFalse(sessionFactory.sessions.single().isVisible.value)
    }

    private class FakeTranslationSessionFactory : TranslationSessionFactory {
        val sessions = mutableListOf<FakeTranslationSession>()

        override fun create(
            timeOrigin: Double,
            isVisible: StateFlow<Boolean>,
            onStateChanged: (TranslationState) -> Unit,
        ): TranslationSession = FakeTranslationSession(timeOrigin, isVisible, onStateChanged).also { sessions += it }
    }

    private class FakeTranslationSession(
        val timeOrigin: Double,
        val isVisible: StateFlow<Boolean>,
        private val onStateChanged: (TranslationState) -> Unit,
    ) : TranslationSession {
        val translated = mutableListOf<LanguagePair>()
        var isEnded = false

        override fun translate(pair: LanguagePair) {
            translated += pair
            onStateChanged(Translated)
        }

        override fun end() {
            isEnded = true
        }
    }

    private companion object {
        val PAIR = LanguagePair("es", "en")
        const val TIME_ORIGIN = 1732634400123.456
    }
}
