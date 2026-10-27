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

import kotlin.random.Random
import androidx.compose.runtime.saveable.Saver as ComposeSaver

class QuizSession(
    presentations: List<QuestionPresentation>,
    val currentSection: QuizSection,
    val currentIndex: Int = 0,
    results: Map<QuestionId, GuessResult> = emptyMap(),
) {
    val presentations: List<QuestionPresentation> = presentations.toList()
    val results: Map<QuestionId, GuessResult> = results.toMap()

    init {
        require(presentations.isNotEmpty())
        require(presentations.map { it.question.id }.distinct().size == presentations.size)
        require(questionsFor(currentSection).isNotEmpty())
        require(currentIndex in questionsFor(currentSection).indices)
    }

    val currentPresentation: QuestionPresentation
        get() = questionsFor(currentSection)[currentIndex]

    val currentResult: GuessResult
        get() = results[currentPresentation.question.id] ?: GuessResult.Unanswered

    val canGoPrevious: Boolean
        get() = currentIndex > 0

    val canGoNext: Boolean
        get() = currentIndex < questionsFor(currentSection).lastIndex

    fun questionsFor(section: QuizSection): List<QuestionPresentation> = presentations.filter { it.question.section == section }

    fun selectSection(section: QuizSection): QuizSession {
        require(questionsFor(section).isNotEmpty())
        return copyWith(currentSection = section, currentIndex = 0)
    }

    fun guess(top: Boolean): QuizSession {
        if (currentResult != GuessResult.Unanswered) return this

        val result = if (top == currentPresentation.composeOnTop) GuessResult.Correct else GuessResult.Incorrect
        return copyWith(results = results + (currentPresentation.question.id to result))
    }

    fun previous(): QuizSession = if (canGoPrevious) copyWith(currentIndex = currentIndex - 1) else this

    fun next(): QuizSession = if (canGoNext) copyWith(currentIndex = currentIndex + 1) else this

    override fun equals(other: Any?): Boolean =
        other is QuizSession &&
            presentations == other.presentations &&
            currentSection == other.currentSection &&
            currentIndex == other.currentIndex &&
            results == other.results

    override fun hashCode(): Int = arrayOf(presentations, currentSection, currentIndex, results).contentHashCode()

    private fun copyWith(
        currentSection: QuizSection = this.currentSection,
        currentIndex: Int = this.currentIndex,
        results: Map<QuestionId, GuessResult> = this.results,
    ): QuizSession = QuizSession(presentations, currentSection, currentIndex, results)
}

object QuizSessionFactory {
    fun create(random: Random, questions: List<QuizQuestion>): QuizSession {
        require(questions.isNotEmpty())
        require(questions.map { it.id }.distinct().size == questions.size)

        val placements = MutableList(questions.size) { index -> index < (questions.size + 1) / 2 }
        placements.shuffle(random)
        val presentations = questions.shuffled(random).zip(placements).map { (question, composeOnTop) ->
            QuestionPresentation(question = question, composeOnTop = composeOnTop)
        }
        return QuizSession(
            presentations = presentations,
            currentSection = presentations.first().question.section,
        )
    }
}

fun QuizSession.saver(): ComposeSaver<QuizSession, QuizSessionSavedState> = ComposeSaver(
    save = { session -> session.toSavedState() },
    restore = { savedState -> savedState.toQuizSession() },
)

@Suppress("FunctionName")
fun QuizSession.Saver(): ComposeSaver<QuizSession, QuizSessionSavedState> = saver()

fun QuizSession.toSavedState(): QuizSessionSavedState = listOf(
    presentations.map { it.question.id.name },
    presentations.map { it.question.section.name },
    presentations.map { it.composeOnTop },
    currentSection.name,
    currentIndex,
    results.keys.map { it.name },
    results.values.map { it.name },
)

@Suppress("UNCHECKED_CAST")
fun QuizSessionSavedState.toQuizSession(): QuizSession {
    require(size == SAVED_STATE_SIZE)
    val ids = this[QUESTION_IDS_INDEX] as List<String>
    val sections = this[QUESTION_SECTIONS_INDEX] as List<String>
    val placements = this[PLACEMENTS_INDEX] as List<Boolean>
    require(ids.size == sections.size && ids.size == placements.size)
    val resultIds = this[RESULT_IDS_INDEX] as List<String>
    val resultValues = this[RESULT_VALUES_INDEX] as List<String>
    require(resultIds.size == resultValues.size)

    return QuizSession(
        presentations = ids.indices.map { index ->
            QuestionPresentation(
                question = QuizQuestion(QuestionId.valueOf(ids[index]), QuizSection.valueOf(sections[index])),
                composeOnTop = placements[index],
            )
        },
        currentSection = QuizSection.valueOf(this[CURRENT_SECTION_INDEX] as String),
        currentIndex = this[CURRENT_INDEX_INDEX] as Int,
        results = resultIds.indices.associate { index ->
            QuestionId.valueOf(resultIds[index]) to GuessResult.valueOf(resultValues[index])
        },
    )
}

private const val QUESTION_IDS_INDEX = 0
private const val QUESTION_SECTIONS_INDEX = 1
private const val PLACEMENTS_INDEX = 2
private const val CURRENT_SECTION_INDEX = 3
private const val CURRENT_INDEX_INDEX = 4
private const val RESULT_IDS_INDEX = 5
private const val RESULT_VALUES_INDEX = 6
private const val SAVED_STATE_SIZE = 7
