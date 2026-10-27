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

package com.duckduckgo.app.dev.settings.quiz

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizSessionTest {

    @Test
    fun `when a full session is created then every question is included once with balanced compose top placements`() {
        val questions = allQuestions()

        val session = QuizSessionFactory.create(Random(1), questions)

        assertEquals(questions.map { it.id }.toSet(), session.presentations.map { it.question.id }.toSet())
        assertEquals(questions.size, session.presentations.size)
        assertTrue(session.presentations.count { it.composeOnTop } in setOf(questions.size / 2, (questions.size + 1) / 2))
    }

    @Test
    fun `when created with the same seed then session presentation order is reproducible`() {
        val questions = allQuestions()

        val first = QuizSessionFactory.create(Random(123), questions)
        val second = QuizSessionFactory.create(Random(123), questions)

        assertEquals(first.presentations, second.presentations)
    }

    @Test
    fun `when created with different seeds then presentations change without changing question coverage`() {
        val questions = allQuestions()
        val first = QuizSessionFactory.create(Random(123), questions)
        val second = QuizSessionFactory.create(Random(456), questions)

        assertNotEquals(first.presentations, second.presentations)
        assertEquals(first.presentations.map { it.question.id }.toSet(), second.presentations.map { it.question.id }.toSet())
    }

    @Test
    fun `when on the first or last question then navigation is constrained to the selected section`() {
        val session = QuizSessionFactory.create(Random(2), allQuestions()).selectSection(QuizSection.Buttons)

        assertFalse(session.canGoPrevious)
        assertTrue(session.canGoNext)
        assertEquals(session, session.previous())

        val last = generateSequence(session) { current -> current.next().takeIf { it != current } }.last()

        assertTrue(last.canGoPrevious)
        assertFalse(last.canGoNext)
        assertEquals(last, last.next())
    }

    @Test
    fun `when guessing the compose placement then result is correct and cannot be changed`() {
        val session = QuizSessionFactory.create(Random(3), allQuestions())
        val firstGuess = session.guess(session.currentPresentation.composeOnTop)

        assertEquals(GuessResult.Correct, firstGuess.currentResult)
        assertEquals(firstGuess, firstGuess.guess(!firstGuess.currentPresentation.composeOnTop))
    }

    @Test
    fun `when guessing the view placement then result is incorrect`() {
        val session = QuizSessionFactory.create(Random(4), allQuestions())

        val guessed = session.guess(!session.currentPresentation.composeOnTop)

        assertEquals(GuessResult.Incorrect, guessed.currentResult)
    }

    @Test
    fun `when choosing top then scoring follows the compose top placement`() {
        val question = QuizQuestion(QuestionId.BrandButton, QuizSection.Buttons)
        val composeOnTop = QuizSession(
            presentations = listOf(QuestionPresentation(question, composeOnTop = true)),
            currentSection = QuizSection.Buttons,
        )
        val composeOnBottom = QuizSession(
            presentations = listOf(QuestionPresentation(question, composeOnTop = false)),
            currentSection = QuizSection.Buttons,
        )

        assertEquals(GuessResult.Correct, composeOnTop.guess(top = true).currentResult)
        assertEquals(GuessResult.Incorrect, composeOnBottom.guess(top = true).currentResult)
    }

    @Test
    fun `when saved and restored then top placement state is equal`() {
        val session = QuizSessionFactory.create(Random(5), allQuestions())
            .selectSection(QuizSection.InputsAndSelection)
            .guess(true)
            .next()
        val saver = session.saver()
        val savedState = with(saver) {
            with(bundleSaveableScope) {
                requireNotNull(save(session))
            }
        }

        assertTrue(bundleSaveableScope.canBeSaved(savedState))
        val restored = with(saver) { restore(savedState) }

        assertEquals(session, restored)
    }

    @Test
    fun `when a section is selected then it starts at that section first question`() {
        val session = QuizSessionFactory.create(Random(6), allQuestions())
            .selectSection(QuizSection.Buttons)
            .next()

        val selected = session.selectSection(QuizSection.StatusAndLoading)

        assertEquals(QuizSection.StatusAndLoading, selected.currentSection)
        assertEquals(0, selected.currentIndex)
        assertFalse(selected.canGoPrevious)
    }

    private fun allQuestions(): List<QuizQuestion> = QuestionId.entries.map { id ->
        QuizQuestion(id = id, section = id.section)
    }

    private companion object {
        val bundleSaveableScope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = when (value) {
                is String, is Int, is Boolean -> true
                is List<*> -> value.all { item -> item != null && canBeSaved(item) }
                else -> false
            }
        }
    }
}
