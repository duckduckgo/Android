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

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R as DesignSystemR

@Composable
fun QuizQuestion(
    session: QuizSession,
    onGuess: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onHome: () -> Unit,
) {
    val presentation = session.currentPresentation
    val specification = ComposeQuizRegistry.question(presentation.question.id)
    val renderer = ComposeQuizRegistry.renderer(presentation.question.id)
    val result = session.currentResult
    val answered = result != GuessResult.Unanswered
    val sectionQuestions = session.questionsFor(session.currentSection)

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        DaxGhostButton(text = stringResource(R.string.composeQuizHome), onClick = onHome)
        DaxText(
            text = specification.title,
            modifier = Modifier.padding(top = 16.dp),
            style = DuckDuckGoTheme.typography.h2,
        )
        DaxText(
            text = stringResource(
                R.string.composeQuizQuestionCounter,
                session.currentIndex + 1,
                sectionQuestions.size,
            ),
            style = DuckDuckGoTheme.typography.caption,
        )
        DaxText(
            text = stringResource(R.string.composeQuizPromptTopBottom),
            modifier = Modifier.padding(vertical = 12.dp),
            style = DuckDuckGoTheme.typography.h4,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuizPane(
                renderer = renderer,
                isCompose = presentation.composeOnTop,
                questionKey = presentation.question.id,
            )
            QuizPane(
                renderer = renderer,
                isCompose = !presentation.composeOnTop,
                questionKey = presentation.question.id,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DaxPrimaryButton(
                text = stringResource(R.string.composeQuizChooseTop),
                onClick = { onGuess(true) },
                enabled = !answered,
                modifier = Modifier.weight(1f),
            )
            DaxPrimaryButton(
                text = stringResource(R.string.composeQuizChooseBottom),
                onClick = { onGuess(false) },
                enabled = !answered,
                modifier = Modifier.weight(1f),
            )
        }

        if (answered) {
            DaxText(
                text = stringResource(
                    if (result == GuessResult.Correct) R.string.composeQuizCorrect else R.string.composeQuizIncorrect,
                ),
                modifier = Modifier.padding(top = 16.dp),
                style = DuckDuckGoTheme.typography.h3,
            )
            DaxText(
                text = stringResource(
                    R.string.composeQuizTopIs,
                    if (presentation.composeOnTop) "Compose" else "View",
                ),
                style = DuckDuckGoTheme.typography.body2,
            )
            DaxText(
                text = stringResource(
                    R.string.composeQuizBottomIs,
                    if (presentation.composeOnTop) "View" else "Compose",
                ),
                style = DuckDuckGoTheme.typography.body2,
            )
            DaxText(
                text = stringResource(
                    R.string.composeQuizTopApi,
                    if (presentation.composeOnTop) specification.composeApiName else specification.viewApiName,
                ),
                style = DuckDuckGoTheme.typography.body2,
            )
            DaxText(
                text = stringResource(
                    R.string.composeQuizBottomApi,
                    if (presentation.composeOnTop) specification.viewApiName else specification.composeApiName,
                ),
                style = DuckDuckGoTheme.typography.body2,
            )
            DaxText(
                text = creditsText(specification.credits),
                modifier = Modifier.padding(top = 8.dp),
                style = DuckDuckGoTheme.typography.body2,
            )
            specification.caveat?.let {
                DaxText(
                    text = it,
                    modifier = Modifier.padding(top = 8.dp),
                    style = DuckDuckGoTheme.typography.caption,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DaxGhostButton(
                text = stringResource(R.string.composeQuizPrevious),
                onClick = onPrevious,
                enabled = session.canGoPrevious,
            )
            DaxPrimaryButton(
                text = stringResource(R.string.composeQuizNext),
                onClick = onNext,
                enabled = session.canGoNext,
            )
        }
    }
}

@Composable
private fun creditsText(credits: List<Credit>): String = credits.mapIndexed { index, credit ->
    when {
        credit.component != null -> stringResource(R.string.composeQuizComponentBy, credit.component, credit.name)
        index == 0 -> stringResource(R.string.composeQuizBuiltBy, credit.name)
        else -> credit.name
    }
}.joinToString(stringResource(R.string.composeQuizCreditSeparator))

@Composable
private fun QuizPane(
    renderer: QuizRenderer,
    isCompose: Boolean,
    questionKey: QuestionId,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        key(questionKey, isCompose) {
            if (isCompose) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = PANE_INSET)) {
                    renderer.ComposeSample()
                }
            } else {
                AndroidView(
                    factory = { context -> insetHost(context, renderer.createView(context)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val PANE_INSET = 4.dp

// The View host clips its children to its bounds, which cuts off outlines drawn at the edge of the view.
private fun insetHost(context: Context, content: View): View {
    val inset = context.resources.getDimensionPixelSize(DesignSystemR.dimen.keyline_1)
    return FrameLayout(context).apply {
        setPadding(inset, 0, inset, 0)
        clipToPadding = false
        clipChildren = false
        addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}

// The pane host stretches its child to the full width, which centres or end-aligns wrap_content controls.
internal fun startAligned(content: View, margin: Int = 0): View = FrameLayout(content.context).apply {
    val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START)
    params.setMargins(margin, margin, margin, margin)
    addView(content, params)
}
