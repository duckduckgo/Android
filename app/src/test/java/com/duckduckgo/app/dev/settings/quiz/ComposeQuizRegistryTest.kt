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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeQuizRegistryTest {

    @Test
    fun `registry exposes each eligible family once in its intended group`() {
        val questions = ComposeQuizRegistry.questions

        assertEquals(33, questions.size)
        assertEquals(QuestionId.entries.toSet(), questions.map { it.id }.toSet())
        assertEquals(
            mapOf(
                QuizSection.Buttons to 8,
                QuizSection.InputsAndSelection to 6,
                QuizSection.Lists to 4,
                QuizSection.StatusAndLoading to 4,
                QuizSection.Dialogs to 5,
                QuizSection.Messaging to 6,
            ),
            questions.groupingBy { it.id.section }.eachCount(),
        )
    }

    @Test
    fun `registry provides a paired renderer for each eligible family`() {
        QuestionId.entries.forEach { id ->
            val renderer = ComposeQuizRegistry.renderer(id)

            assertTrue(renderer.javaClass.interfaces.contains(QuizRenderer::class.java))
        }
    }

    @Test
    fun `registry reveals the concrete APIs used by each renderer pair`() {
        val revealedApis = ComposeQuizRegistry.questions.associate { it.id to (it.viewApiName to it.composeApiName) }

        assertEquals("DaxTextInput" to "DaxTextField", revealedApis.getValue(QuestionId.TextField))
        assertEquals("Snackbar" to "DaxSnackbar", revealedApis.getValue(QuestionId.Snackbar))
        assertEquals("TextAlertDialogBuilder" to "DaxTextAlertDialog", revealedApis.getValue(QuestionId.TextDialog))
        assertEquals("InfoPanel" to "DaxInfoPanel", revealedApis.getValue(QuestionId.InfoPanel))
        assertEquals(
            "SkeletonView + ShimmerFrameLayout" to "DaxSkeletonListItem + DaxSkeletonSectionHeader",
            revealedApis.getValue(QuestionId.SkeletonListHeader),
        )
    }

    @Test
    fun `registry omits candidates with no strict view compose equivalent`() {
        val exposedTitles = ComposeQuizRegistry.questions.map(QuestionSpec::title)

        assertTrue("Search bar" !in exposedTitles)
        assertTrue("Top app bar" !in exposedTitles)
        assertTrue("Chip" !in exposedTitles)
    }

    @Test
    fun `only platform view comparisons disclose the required caveat`() {
        val caveats = ComposeQuizRegistry.questions
            .filter { it.caveat != null }
            .associate { it.id to requireNotNull(it.caveat) }

        assertEquals(setOf(QuestionId.Checkbox, QuestionId.Slider, QuestionId.ProgressSpinner), caveats.keys)
        assertTrue(caveats.getValue(QuestionId.Checkbox).contains("platform View CheckBox vs ADS Compose DaxCheckbox"))
        assertTrue(caveats.getValue(QuestionId.Slider).contains("Material Slider vs ADS Compose DaxSlider"))
        assertTrue(caveats.getValue(QuestionId.ProgressSpinner).contains("Material CircularProgressIndicator vs ADS Compose DaxProgressSpinner"))
    }

    @Test
    fun `every question credits at least one person and list items credit their dropdown`() {
        ComposeQuizRegistry.questions.forEach { assertTrue(it.id.name, it.credits.isNotEmpty()) }
        assertEquals(
            listOf(Credit("Mike"), Credit("Gerard", "Dropdown")),
            ComposeQuizRegistry.question(QuestionId.OneLineListItem).credits,
        )
    }
}
