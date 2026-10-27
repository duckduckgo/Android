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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.random.Random

@Composable
fun ComposeQuizApp() {
    val initialSession = remember {
        QuizSessionFactory.create(
            random = Random.Default,
            questions = ComposeQuizRegistry.questions.map { QuizQuestion(it.id, it.id.section) },
        )
    }
    val sessionState = rememberSaveable(
        saver = Saver(
            save = { it.value.toSavedState() },
            restore = { mutableStateOf(it.toQuizSession()) },
        ),
    ) { mutableStateOf(initialSession) }
    val session = sessionState.value
    val showingQuestionState = rememberSaveable { mutableStateOf(false) }

    if (showingQuestionState.value) {
        QuizQuestion(
            session = session,
            onGuess = { top -> sessionState.value = session.guess(top) },
            onPrevious = { sessionState.value = session.previous() },
            onNext = { sessionState.value = session.next() },
            onHome = { showingQuestionState.value = false },
        )
    } else {
        QuizHome(
            session = session,
            onSectionSelected = { section ->
                sessionState.value = session.selectSection(section)
                showingQuestionState.value = true
            },
        )
    }
}
