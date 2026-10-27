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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.cards.DaxCard
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

@Composable
fun QuizHome(
    session: QuizSession,
    onSectionSelected: (QuizSection) -> Unit,
) {
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            DaxText(
                text = stringResource(R.string.composeQuizInstructionVertical),
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        items(QuizSection.entries) { section ->
            val count = session.questionsFor(section).size
            DaxCard(
                onClick = { onSectionSelected(section) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DaxText(
                        text = stringResource(section.titleRes),
                        style = DuckDuckGoTheme.typography.h3,
                    )
                    DaxText(
                        text = stringResource(R.string.composeQuizGroupCount, count),
                        style = DuckDuckGoTheme.typography.caption,
                    )
                }
            }
        }
    }
}

private val QuizSection.titleRes: Int
    get() = when (this) {
        QuizSection.Buttons -> R.string.composeQuizGroupButtons
        QuizSection.InputsAndSelection -> R.string.composeQuizGroupInputs
        QuizSection.Lists -> R.string.composeQuizGroupLists
        QuizSection.StatusAndLoading -> R.string.composeQuizGroupStatus
        QuizSection.Dialogs -> R.string.composeQuizGroupDialogs
        QuizSection.Messaging -> R.string.composeQuizGroupMessaging
    }
