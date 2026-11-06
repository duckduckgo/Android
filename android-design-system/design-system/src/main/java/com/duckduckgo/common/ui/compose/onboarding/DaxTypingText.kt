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

package com.duckduckgo.common.ui.compose.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import kotlinx.coroutines.delay
import java.text.BreakIterator
import java.text.StringCharacterIterator

/**
 * Internal title text that keeps the complete layout stable while revealing grapheme clusters.
 *
 * The complete [text] is laid out once and the reveal clips it to the typed range in the draw phase.
 * [snap] and an intro-sampled non-animated decision use the same settled path and invoke [onFinished] at most once for this text composition.
 */
@Composable
internal fun DaxTypingText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: DuckDuckGoTextStyle = DuckDuckGoTheme.typography.body1,
    color: Color = DuckDuckGoTheme.textColors.primary,
    textAlign: TextAlign? = null,
    animate: Boolean = true,
    snap: Boolean = false,
    onFinished: () -> Unit = {},
) {
    val initialText = remember { text }
    var visibleEnd by remember(text) { mutableIntStateOf(0) }
    var finished by remember(text) { mutableStateOf(false) }
    val latestOnFinished by rememberUpdatedState(onFinished)

    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }

    LaunchedEffect(text, animate, snap) {
        if (finished) return@LaunchedEffect

        fun finish() {
            if (!finished) {
                visibleEnd = text.length
                finished = true
                latestOnFinished()
            }
        }

        if (typingAnimationShouldSnap(animate, snap || text != initialText)) {
            finish()
            return@LaunchedEffect
        }

        revealGraphemes(text.text.graphemeEnds()) { visibleEnd = it }
        finish()
    }

    DaxText(
        text = text,
        modifier = modifier
            .clearAndSetSemantics { this.text = text }
            .drawWithContent {
                val displayedEnd = if (!animate || snap || finished || text != initialText) text.length else visibleEnd
                val result = layout
                when {
                    displayedEnd >= text.length -> drawContent()
                    result == null -> Unit
                    else -> clipPath(result.getPathForRange(0, displayedEnd)) { this@drawWithContent.drawContent() }
                }
            },
        style = style,
        color = color,
        textAlign = textAlign,
        onTextLayout = { layout = it },
    )
}

internal fun typingAnimationShouldSnap(
    animate: Boolean,
    snap: Boolean,
): Boolean = snap || !animate

private fun String.graphemeEnds(): List<Int> {
    val iterator = BreakIterator.getCharacterInstance().apply {
        text = StringCharacterIterator(this@graphemeEnds)
    }
    return generateSequence { iterator.next() }
        .takeWhile { it != BreakIterator.DONE }
        .toList()
}

@PreviewLightDark
@Composable
private fun DaxTypingTextPreview() {
    PreviewBox {
        DaxTypingText(
            text = AnnotatedString(
                "A longer onboarding title can span multiple lines so the final layout is visible " +
                    "before the typing animation starts.",
            ),
            style = DuckDuckGoTheme.typography.onboardingTitle,
            snap = true,
        )
    }
}

@PreviewFontScale
@PreviewLightDark
@Composable
private fun DaxTypingTextFontScalePreview() {
    PreviewBox {
        DaxTypingText(
            text = AnnotatedString(
                "A longer onboarding title can span multiple lines so the final layout is visible " +
                    "before the typing animation starts.",
            ),
            style = DuckDuckGoTheme.typography.onboardingTitle,
            snap = true,
        )
    }
}

internal suspend fun revealGraphemes(graphemeEnds: List<Int>, onReveal: (Int) -> Unit) {
    if (graphemeEnds.isEmpty()) return
    // Frame time lets a slow frame catch up instead of accumulating a delay for each grapheme.
    animate(
        initialValue = 0f,
        targetValue = graphemeEnds.size.toFloat(),
        animationSpec = tween((DaxOnboardingDialogDefaults.TypingCadence * graphemeEnds.size).inWholeMilliseconds.toInt(), easing = LinearEasing),
    ) { progress, _ ->
        onReveal(graphemeEnds[progress.toInt().coerceAtMost(graphemeEnds.lastIndex)])
    }
    delay(DaxOnboardingDialogDefaults.TypingSettle)
}
