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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.mobile.android.R
import kotlin.time.Duration

/**
 * Onboarding dialog drawn as a speech bubble, with a typed title, optional body, header image,
 * content slot, primary button, close button and step indicator.
 *
 * Changing [contentKey] plays the intro again. Call inside [OnboardingTheme]. The dialog scrolls its
 * own content, so content must not add another vertical scroller.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1207908166761516/task/1219997332635983
 * Figma reference: https://www.figma.com/design/wMxBpe0mKrRS0nVhtwMGO7/%F0%9F%9A%80-Onboarding-Components--2026-?node-id=3005-41695
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
 * @param step Optional step indicator shown on the top edge.
 * @param headerToActionsSpacing Space between the text and the content or button.
 * @param textHorizontalPadding Horizontal padding for the title and body.
 * @param contentHorizontalPadding Horizontal padding for the content slot.
 * @param content Optional content shown below the body.
 */
@Composable
fun DaxOnboardingDialog(
    contentKey: String,
    title: String,
    modifier: Modifier = Modifier,
    state: DaxOnboardingDialogState = rememberDaxOnboardingDialogState(),
    animate: Boolean = true,
    entryDelay: Duration = Duration.ZERO,
    textAlign: DaxOnboardingDialogTextAlign = DaxOnboardingDialogTextAlign.Center,
    body: AnnotatedString? = null,
    headerImage: Painter? = null,
    boundsAnimationDuration: Duration = DaxOnboardingDialogDefaults.BoundsAnimationDuration,
    tail: DaxOnboardingDialogTail = DaxOnboardingDialogTail.None,
    tailExtended: Boolean = true,
    primaryCta: DaxOnboardingDialogCta? = null,
    onIntroFinished: (contentKey: String) -> Unit = {},
    dismiss: DaxOnboardingDialogDismiss? = null,
    step: DaxOnboardingDialogStep? = null,
    headerToActionsSpacing: Dp = DaxOnboardingDialogDefaults.HeaderContentSpacing,
    textHorizontalPadding: Dp = DaxOnboardingDialogDefaults.TextHorizontalPadding,
    contentHorizontalPadding: Dp = DaxOnboardingDialogDefaults.ContentHorizontalPadding,
    content: (@Composable DaxOnboardingDialogContentScope.() -> Unit)? = null,
) {
    DaxOnboardingDialogCore(
        useNativeCtaInsets = true,
        showScrollbar = true,
        contentKey = contentKey,
        title = AnnotatedString(title),
        titleStyle = DuckDuckGoTheme.typography.onboardingTitle,
        modifier = modifier,
        body = body?.let { { DaxOnboardingDialogBodyText(it, DuckDuckGoTheme.typography.onboardingBody, textAlign) } },
        state = state,
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
        step = step,
        headerToActionsSpacing = headerToActionsSpacing,
        textHorizontalPadding = textHorizontalPadding,
        contentHorizontalPadding = contentHorizontalPadding,
        content = content,
    )
}

/**
 * String-body overload of [DaxOnboardingDialog]; see it for the other parameters.
 *
 * @param body Plain body text.
 */
@Composable
fun DaxOnboardingDialog(
    contentKey: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    state: DaxOnboardingDialogState = rememberDaxOnboardingDialogState(),
    animate: Boolean = true,
    entryDelay: Duration = Duration.ZERO,
    textAlign: DaxOnboardingDialogTextAlign = DaxOnboardingDialogTextAlign.Center,
    headerImage: Painter? = null,
    boundsAnimationDuration: Duration = DaxOnboardingDialogDefaults.BoundsAnimationDuration,
    tail: DaxOnboardingDialogTail = DaxOnboardingDialogTail.None,
    tailExtended: Boolean = true,
    primaryCta: DaxOnboardingDialogCta? = null,
    onIntroFinished: (contentKey: String) -> Unit = {},
    dismiss: DaxOnboardingDialogDismiss? = null,
    step: DaxOnboardingDialogStep? = null,
    content: (@Composable DaxOnboardingDialogContentScope.() -> Unit)? = null,
) {
    DaxOnboardingDialogCore(
        useNativeCtaInsets = true,
        showScrollbar = true,
        contentKey = contentKey,
        title = AnnotatedString(title),
        titleStyle = DuckDuckGoTheme.typography.onboardingTitle,
        modifier = modifier,
        body = { DaxOnboardingDialogBodyText(AnnotatedString(body), DuckDuckGoTheme.typography.onboardingBody, textAlign) },
        state = state,
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
        step = step,
        content = content,
    )
}

/**
 * Composable-body overload of [DaxOnboardingDialog] for bodies with inline content. See [DaxOnboardingDialog]
 * for the other parameters.
 *
 * @param body Body content.
 */
@Composable
fun DaxOnboardingDialog(
    contentKey: String,
    title: String,
    body: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    state: DaxOnboardingDialogState = rememberDaxOnboardingDialogState(),
    animate: Boolean = true,
    entryDelay: Duration = Duration.ZERO,
    textAlign: DaxOnboardingDialogTextAlign = DaxOnboardingDialogTextAlign.Center,
    headerImage: Painter? = null,
    boundsAnimationDuration: Duration = DaxOnboardingDialogDefaults.BoundsAnimationDuration,
    tail: DaxOnboardingDialogTail = DaxOnboardingDialogTail.None,
    tailExtended: Boolean = true,
    primaryCta: DaxOnboardingDialogCta? = null,
    onIntroFinished: (contentKey: String) -> Unit = {},
    dismiss: DaxOnboardingDialogDismiss? = null,
    step: DaxOnboardingDialogStep? = null,
    content: (@Composable DaxOnboardingDialogContentScope.() -> Unit)? = null,
) {
    DaxOnboardingDialogCore(
        useNativeCtaInsets = true,
        showScrollbar = true,
        contentKey = contentKey,
        title = AnnotatedString(title),
        titleStyle = DuckDuckGoTheme.typography.onboardingTitle,
        modifier = modifier,
        body = body,
        state = state,
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
        step = step,
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun DaxOnboardingDialogDefaultPreview() {
    OnboardingPreview {
        DaxOnboardingDialog(
            contentKey = "centered-default",
            title = "Welcome to DuckDuckGo",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxOnboardingDialogAnnotatedBodyPreview() {
    OnboardingPreview {
        DaxOnboardingDialog(
            contentKey = "centered-annotated",
            title = "Private by default",
            body = buildAnnotatedString {
                append("Your tabs stay private with ")
                addStyle(SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), 0, 22)
                append("DuckDuckGo.")
            },
            headerImage = painterResource(R.drawable.ic_dax_icon),
            tail = DaxOnboardingDialogTail.BottomStart,
            dismiss = DaxOnboardingDialogDismiss("Dismiss dialog") {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxOnboardingDialogStringBodyPreview() {
    OnboardingPreview {
        DaxOnboardingDialog(
            contentKey = "centered-string",
            title = "Try a private search",
            body = "Search without being tracked across sites.",
            animate = false,
            tail = DaxOnboardingDialogTail.BottomStartWide,
            tailExtended = false,
            primaryCta = DaxOnboardingDialogCta("Continue") {},
        )
    }
}

@SuppressLint("InvalidDaxTextColorUsage") // OnboardingTheme owns the binding-required content color.
@PreviewLightDark
@Composable
private fun DaxOnboardingDialogComposableBodyPreview() {
    OnboardingPreview {
        DaxOnboardingDialog(
            contentKey = "centered-composable",
            title = "Inline body content",
            body = {
                DaxText(
                    text = "A body can include inline content supplied by the caller.",
                    style = DuckDuckGoTheme.typography.onboardingBody,
                    color = DaxOnboardingDialogDefaults.ContentColor,
                )
            },
            animate = false,
            tail = DaxOnboardingDialogTail.BottomEnd,
        ) {
            DaxText(
                text = "Arbitrary content appears below the body.",
                color = DaxOnboardingDialogDefaults.ContentColor,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxOnboardingDialogEmptyTitlePreview() {
    OnboardingPreview {
        DaxOnboardingDialog(
            contentKey = "centered-empty-title",
            title = "",
            body = "The empty title skips the typing phase.",
            animate = false,
            tail = DaxOnboardingDialogTail.None,
        )
    }
}

@SuppressLint("InvalidDaxTextColorUsage") // OnboardingTheme owns the binding-required content color.
@PreviewFontScale
@PreviewLightDark
@Composable
private fun DaxOnboardingDialogLongContentPreview() {
    OnboardingPreview {
        DaxOnboardingDialog(
            contentKey = "centered-long",
            title = "A longer title shows the whole card resizing as one bounded composition",
            body = "This is intentionally long content so font scaling and card bounds can be reviewed without a nested scroller.",
            animate = false,
            textAlign = DaxOnboardingDialogTextAlign.Start,
            boundsAnimationDuration = Duration.ZERO,
        ) {
            DaxText(
                text = "Long content remains inside the dialog's single vertical scroller and is keyed to the content key.",
                color = DaxOnboardingDialogDefaults.ContentColor,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxOnboardingDialogContentSwapPreview() {
    OnboardingPreview {
        var contentIndex by remember { mutableIntStateOf(0) }
        val isFirstContent = contentIndex == 0
        DaxOnboardingDialog(
            contentKey = if (isFirstContent) "content-one" else "content-two",
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
