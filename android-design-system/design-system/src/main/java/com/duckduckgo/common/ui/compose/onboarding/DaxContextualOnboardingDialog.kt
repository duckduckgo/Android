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

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.mobile.android.R
import kotlin.time.Duration

/**
 * In-context onboarding dialog shown over a page, drawn as a speech bubble with a typed title,
 * optional body, header image, content slot, primary button and close button. In landscape the
 * text and actions sit side by side.
 *
 * Changing [contentKey] plays the intro again. Call inside [OnboardingTheme]. The dialog scrolls its
 * own content, so content must not add another vertical scroller.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1207908166761516/task/1219997357211788
 * Figma reference: https://www.figma.com/design/wMxBpe0mKrRS0nVhtwMGO7/%F0%9F%9A%80-Onboarding-Components--2026-?node-id=3003-38264
 *
 * @param contentKey Identifies the current content; changing it plays the intro for the new content.
 * @param title Title typed out during the intro; an empty string skips the typing.
 * @param modifier Modifier applied to the dialog root.
 * @param state Lets the caller observe and skip the intro.
 * @param animate Whether the intro and later tail and size changes animate.
 * @param entryDelay Extra delay before the intro starts.
 * @param textAlign Horizontal alignment for the title and body.
 * @param body Optional styled body text.
 * @param headerImage Optional image shown above the title.
 * @param boundsAnimationDuration Duration of the card's size animation.
 * @param tail Position of the speech-bubble tail.
 * @param tailExtended Whether the tail is shown at full depth.
 * @param primaryCta Optional primary button.
 * @param onIntroFinished Called with the content key when its intro finishes or is skipped.
 * @param dismiss Optional close button and its content description.
 * @param content Optional content shown below the body.
 */
@Composable
fun DaxContextualOnboardingDialog(
    contentKey: String,
    title: String,
    modifier: Modifier = Modifier,
    state: DaxOnboardingDialogState = rememberDaxOnboardingDialogState(),
    animate: Boolean = true,
    entryDelay: Duration = Duration.ZERO,
    textAlign: DaxOnboardingDialogTextAlign = DaxOnboardingDialogTextAlign.Start,
    body: AnnotatedString? = null,
    headerImage: Painter? = null,
    boundsAnimationDuration: Duration = DaxOnboardingDialogDefaults.BoundsAnimationDuration,
    tail: DaxOnboardingDialogTail = DaxOnboardingDialogTail.None,
    tailExtended: Boolean = true,
    primaryCta: DaxOnboardingDialogCta? = null,
    onIntroFinished: (contentKey: String) -> Unit = {},
    dismiss: DaxOnboardingDialogDismiss? = null,
    content: (@Composable DaxOnboardingDialogContentScope.() -> Unit)? = null,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    DaxOnboardingDialogCore(
        contentKey = contentKey,
        title = AnnotatedString(title),
        titleStyle = DuckDuckGoTheme.typography.onboardingTitleInContext,
        modifier = modifier,
        body = body?.let { { DaxOnboardingDialogBodyText(it, DuckDuckGoTheme.typography.onboardingBodyInContext, textAlign) } },
        state = state.impl,
        animate = animate,
        entryDelay = entryDelay,
        textAlign = textAlign,
        headerImage = headerImage,
        boundsAnimationDuration = boundsAnimationDuration,
        tail = tail,
        tailExtended = tailExtended,
        primaryCta = primaryCta,
        onIntroFinished = onIntroFinished,
        dismiss = dismiss,
        content = content,
        landscapeLayout = landscape,
        topPadding = if (landscape) {
            DaxOnboardingDialogDefaults.ContextualLandscapeVerticalPadding
        } else {
            DaxOnboardingDialogDefaults.ContextualTopPadding
        },
        bottomPadding = if (landscape) {
            DaxOnboardingDialogDefaults.ContextualLandscapeVerticalPadding
        } else {
            DaxOnboardingDialogDefaults.BottomPadding
        },
        titleBodySpacing = DaxOnboardingDialogDefaults.ContextualTitleBodySpacing,
        elevation = DaxOnboardingDialogDefaults.ContextualElevation,
    )
}

/**
 * String-body overload of [DaxContextualOnboardingDialog]; see it for the other parameters.
 *
 * @param body Plain body text.
 */
@Composable
fun DaxContextualOnboardingDialog(
    contentKey: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    state: DaxOnboardingDialogState = rememberDaxOnboardingDialogState(),
    animate: Boolean = true,
    entryDelay: Duration = Duration.ZERO,
    textAlign: DaxOnboardingDialogTextAlign = DaxOnboardingDialogTextAlign.Start,
    headerImage: Painter? = null,
    boundsAnimationDuration: Duration = DaxOnboardingDialogDefaults.BoundsAnimationDuration,
    tail: DaxOnboardingDialogTail = DaxOnboardingDialogTail.None,
    tailExtended: Boolean = true,
    primaryCta: DaxOnboardingDialogCta? = null,
    onIntroFinished: (contentKey: String) -> Unit = {},
    dismiss: DaxOnboardingDialogDismiss? = null,
    content: (@Composable DaxOnboardingDialogContentScope.() -> Unit)? = null,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    DaxOnboardingDialogCore(
        contentKey = contentKey,
        title = AnnotatedString(title),
        titleStyle = DuckDuckGoTheme.typography.onboardingTitleInContext,
        modifier = modifier,
        body = { DaxOnboardingDialogBodyText(AnnotatedString(body), DuckDuckGoTheme.typography.onboardingBodyInContext, textAlign) },
        state = state.impl,
        animate = animate,
        entryDelay = entryDelay,
        textAlign = textAlign,
        headerImage = headerImage,
        boundsAnimationDuration = boundsAnimationDuration,
        tail = tail,
        tailExtended = tailExtended,
        primaryCta = primaryCta,
        onIntroFinished = onIntroFinished,
        dismiss = dismiss,
        content = content,
        landscapeLayout = landscape,
        topPadding = if (landscape) {
            DaxOnboardingDialogDefaults.ContextualLandscapeVerticalPadding
        } else {
            DaxOnboardingDialogDefaults.ContextualTopPadding
        },
        bottomPadding = if (landscape) {
            DaxOnboardingDialogDefaults.ContextualLandscapeVerticalPadding
        } else {
            DaxOnboardingDialogDefaults.BottomPadding
        },
        titleBodySpacing = DaxOnboardingDialogDefaults.ContextualTitleBodySpacing,
        elevation = DaxOnboardingDialogDefaults.ContextualElevation,
    )
}

/**
 * Composable-body overload of [DaxContextualOnboardingDialog] for bodies with inline content. See [DaxContextualOnboardingDialog]
 * for the other parameters.
 *
 * @param body Body content.
 */
@Composable
fun DaxContextualOnboardingDialog(
    contentKey: String,
    title: String,
    body: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    state: DaxOnboardingDialogState = rememberDaxOnboardingDialogState(),
    animate: Boolean = true,
    entryDelay: Duration = Duration.ZERO,
    textAlign: DaxOnboardingDialogTextAlign = DaxOnboardingDialogTextAlign.Start,
    headerImage: Painter? = null,
    boundsAnimationDuration: Duration = DaxOnboardingDialogDefaults.BoundsAnimationDuration,
    tail: DaxOnboardingDialogTail = DaxOnboardingDialogTail.None,
    tailExtended: Boolean = true,
    primaryCta: DaxOnboardingDialogCta? = null,
    onIntroFinished: (contentKey: String) -> Unit = {},
    dismiss: DaxOnboardingDialogDismiss? = null,
    content: (@Composable DaxOnboardingDialogContentScope.() -> Unit)? = null,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    DaxOnboardingDialogCore(
        contentKey = contentKey,
        title = AnnotatedString(title),
        titleStyle = DuckDuckGoTheme.typography.onboardingTitleInContext,
        modifier = modifier,
        body = body,
        state = state.impl,
        animate = animate,
        entryDelay = entryDelay,
        textAlign = textAlign,
        headerImage = headerImage,
        boundsAnimationDuration = boundsAnimationDuration,
        tail = tail,
        tailExtended = tailExtended,
        primaryCta = primaryCta,
        onIntroFinished = onIntroFinished,
        dismiss = dismiss,
        content = content,
        landscapeLayout = landscape,
        topPadding = if (landscape) {
            DaxOnboardingDialogDefaults.ContextualLandscapeVerticalPadding
        } else {
            DaxOnboardingDialogDefaults.ContextualTopPadding
        },
        bottomPadding = if (landscape) {
            DaxOnboardingDialogDefaults.ContextualLandscapeVerticalPadding
        } else {
            DaxOnboardingDialogDefaults.BottomPadding
        },
        titleBodySpacing = DaxOnboardingDialogDefaults.ContextualTitleBodySpacing,
        elevation = DaxOnboardingDialogDefaults.ContextualElevation,
    )
}

@PreviewLightDark
@Composable
private fun DaxContextualOnboardingDialogDefaultPreview() {
    OnboardingPreview {
        DaxContextualOnboardingDialog(
            contentKey = "contextual-default",
            title = "A private browser in context",
            body = "This contextual dialog keeps its text aligned to the start.",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxContextualOnboardingDialogAnnotatedBodyPreview() {
    OnboardingPreview {
        DaxContextualOnboardingDialog(
            contentKey = "contextual-annotated",
            title = "Inline styling stays intact",
            body = buildAnnotatedString {
                append("Read the ")
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), 9, 15)
                append("details.")
            },
            headerImage = painterResource(R.drawable.ic_dax_icon),
            tail = DaxOnboardingDialogTail.BottomStart,
            tailExtended = false,
            dismiss = DaxOnboardingDialogDismiss("Dismiss contextual dialog") {},
        )
    }
}

@SuppressLint("InvalidDaxTextColorUsage") // OnboardingTheme owns the binding-required content color.
@PreviewLightDark
@Composable
private fun DaxContextualOnboardingDialogComposableBodyPreview() {
    OnboardingPreview {
        DaxContextualOnboardingDialog(
            contentKey = "contextual-composable",
            title = "Rich body content",
            body = {
                DaxText(
                    text = "Inline content can be supplied by a caller.",
                    style = DuckDuckGoTheme.typography.onboardingBodyInContext,
                    color = DaxOnboardingDialogDefaults.ContentColor,
                )
            },
            animate = false,
            textAlign = DaxOnboardingDialogTextAlign.Center,
            tail = DaxOnboardingDialogTail.BottomEnd,
        ) {
            DaxText(
                text = "Content remains keyed by contentKey.",
                color = DaxOnboardingDialogDefaults.ContentColor,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxContextualOnboardingDialogEmptyTitlePreview() {
    OnboardingPreview {
        DaxContextualOnboardingDialog(
            contentKey = "contextual-empty-title",
            title = "",
            body = "An empty title is the no-title choreography case.",
            animate = false,
            tail = DaxOnboardingDialogTail.BottomStartWide,
            primaryCta = DaxOnboardingDialogCta("Continue") {},
        )
    }
}

@SuppressLint("InvalidDaxTextColorUsage") // OnboardingTheme owns the binding-required content color.
@PreviewFontScale
@PreviewLightDark
@Composable
private fun DaxContextualOnboardingDialogLongContentPreview() {
    OnboardingPreview {
        DaxContextualOnboardingDialog(
            contentKey = "contextual-long",
            title = "A longer contextual title shows the whole card at larger font scales",
            body = "The in-context body keeps its 16sp/22sp typography while the card stays in one bounded scroller.",
            animate = false,
            boundsAnimationDuration = Duration.ZERO,
        ) {
            DaxText(
                text = "The slot is cleaned up when contentKey changes; use DisposableEffect for work tied to its lifetime.",
                color = DaxOnboardingDialogDefaults.ContentColor,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxContextualOnboardingDialogRtlPreview() {
    OnboardingPreview {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            DaxContextualOnboardingDialog(
                contentKey = "contextual-rtl",
                title = "Contextual RTL",
                body = "Tail positions are relative to layout direction.",
                animate = false,
                tail = DaxOnboardingDialogTail.BottomEnd,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxContextualOnboardingDialogContentSwapPreview() {
    OnboardingPreview {
        var contentIndex by remember { mutableIntStateOf(0) }
        val isFirstContent = contentIndex == 0
        DaxContextualOnboardingDialog(
            contentKey = if (isFirstContent) "contextual-content-one" else "contextual-content-two",
            title = if (isFirstContent) "First content" else "Second content",
            body = if (isFirstContent) {
                "Tap the CTA to replace this presentation with the second content."
            } else {
                "This is the replacement content. Tap the CTA to return to the first."
            },
            animate = false,
            primaryCta = DaxOnboardingDialogCta(
                text = if (isFirstContent) "Show second" else "Show first",
                onClick = { contentIndex = 1 - contentIndex },
            ),
        )
    }
}
