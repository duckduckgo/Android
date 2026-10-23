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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R
import kotlin.time.Duration

/**
 * Fully Compose onboarding dialog with centered 24sp/28sp title and 18sp/22sp body typography.
 *
 * [contentKey] identifies the current content. A new key or mount starts an intro; a same-key
 * update preserves it. Only the current content is composed; callers own navigation and step state.
 * [animate] and [entryDelay] are sampled when an intro starts. [entryDelay] is added to the 200ms
 * base entrance delay; settled tail and bounds changes use the latest [animate] value. Restore
 * completed host content with `animate = false` to avoid replaying its intro after recreation.
 * A same-key title change during typing shows the updated title fully and advances the intro.
 *
 * [onIntroFinished] is delivered once after composition for the completed run, including snap or
 * skip; replacing an unfinished run cancels its callback. It reports intro completion, not a
 * product-step completion. [DaxOnboardingDialogState.skip] only finishes an attached intro and
 * does not queue a skip. Disposal detaches state and resets its `isAnimating` to false.
 * The first pointer gesture during an intro finishes it and is consumed
 * completely; a subsequent gesture can activate a CTA.
 *
 * This composable must be called inside [OnboardingTheme]; it reads onboarding colours from that
 * provider, and omitting it causes the backing composition local to throw.
 *
 * The dialog owns one bounded vertical scroller, so caller content must not add a nested vertical
 * scroller. Content and scroll state are keyed by [contentKey]; the shell and tail are retained.
 * Cancel long-lived content work from a `DisposableEffect` and register
 * [DaxOnboardingDialogContentScope.onContentReady] for the shared ready signal. Scope `enabled`
 * becomes true when the intro settles, and the ready callback reports natural animation (`true`)
 * or snap/skip (`false`) once after composition. Bounds snap during the intro and animate settled
 * same-key changes. Inspection previews and reduced motion show settled content.
 * The primary CTA currently uses the existing ADS button placeholder; a dedicated onboarding
 * brand button is intentionally a separate component.
 * Tail geometry is logical-direction relative: `None` is flat, `BottomStart` is 80dp from logical
 * start, `BottomStartWide` is 96dp, and `BottomEnd` is 80dp from logical end. `tailExtended = false`
 * retracts the tail depth; `true` fully extends it. `dismiss` is a 32dp affordance at logical
 * top/end with a 4dp visual overhang; hit and semantics bounds remain inside the layout.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1207908166761516/task/1219997332635983
 * Figma reference: https://www.figma.com/design/wMxBpe0mKrRS0nVhtwMGO7/%F0%9F%9A%80-Onboarding-Components--2026-?node-id=3005-41695
 *
 * @param contentKey Stable identity for the current content; change it to start a new intro.
 * @param title Title text; pass an empty string for the no-title choreography case.
 * @param modifier Modifier applied to the dialog root.
 * @param state Hoisted state used to skip the current introduction.
 * @param animate Whether the next intro and settled tail/bounds changes animate.
 * @param entryDelay Extra delay added to the 200ms entrance delay.
 * @param textAlign Horizontal alignment for title and body text.
 * @param body Optional styled body text.
 * @param headerImage Optional image displayed above the title.
 * @param boundsAnimationDuration Duration used when the card bounds change.
 * @param tail Speech-bubble tail position.
 * @param tailExtended Whether the configured tail is fully extended.
 * @param primaryCta Optional primary CTA.
 * @param onIntroFinished Called after composition with the completed run’s content key; canceled runs do not call it.
 * @param dismiss Optional dismiss action and accessible content description.
 * @param step Optional step indicator centred on the painted top edge; `null` hides it.
 * @param textHorizontalPadding Horizontal inset for the title and optional body.
 * @param contentHorizontalPadding Horizontal inset for the content slot, independent of CTA insets.
 * @param content Optional content-keyed arbitrary content slot.
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
        body = body?.let { dialogBody(it, DuckDuckGoTheme.typography.onboardingBody, textAlign) },
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
 * String-body overload of [DaxOnboardingDialog]. The body is converted to an [AnnotatedString]
 * before it reaches the stable core.
 *
 * This composable must be called inside [OnboardingTheme]; it reads onboarding colours from that
 * provider, and omitting it causes the backing composition local to throw.
 *
 * The content-key, intro-callback, bounded-scroller, keyed content, cleanup, and CTA
 * contracts are the same as the primary overload. `animate` and `entryDelay` are sampled at
 * intro start; settled tail and bounds use the latest `animate`. The callback reports intro
 * completion rather than product-step completion.
 * Tail geometry is logical-direction relative: `None` is flat, `BottomStart` is 80dp from logical
 * start, `BottomStartWide` is 96dp, and `BottomEnd` is 80dp from logical end. `tailExtended = false`
 * retracts the tail depth; `true` fully extends it. `dismiss` is a 32dp affordance at logical
 * top/end with a 4dp visual overhang; hit and semantics bounds remain inside the layout.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1207908166761516/task/1219997332635983
 * Figma reference: https://www.figma.com/design/wMxBpe0mKrRS0nVhtwMGO7/%F0%9F%9A%80-Onboarding-Components--2026-?node-id=3005-41695
 *
 * @param contentKey Stable identity for the current content; change it to start a new intro.
 * @param title Title text; pass an empty string for the no-title choreography case.
 * @param body Required plain body text.
 * @param modifier Modifier applied to the dialog root.
 * @param state Hoisted state used to skip the current introduction.
 * @param animate Whether the next intro and settled tail/bounds changes animate.
 * @param entryDelay Extra delay added to the 200ms entrance delay.
 * @param textAlign Horizontal alignment for title and body text.
 * @param headerImage Optional image displayed above the title.
 * @param boundsAnimationDuration Duration used when the card bounds change.
 * @param tail Speech-bubble tail position.
 * @param tailExtended Whether the configured tail is fully extended.
 * @param primaryCta Optional primary CTA.
 * @param onIntroFinished Called after composition with the completed run’s content key; canceled runs do not call it.
 * @param dismiss Optional dismiss action and accessible content description.
 * @param step Optional step indicator centred on the painted top edge; `null` hides it.
 * @param content Optional content-keyed arbitrary content slot.
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
        body = dialogBody(AnnotatedString(body), DuckDuckGoTheme.typography.onboardingBody, textAlign),
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
 * Composable-body overload of [DaxOnboardingDialog] for inline icons and other rich content.
 *
 * The body lambda is required and is intentionally not trailing; the sole trailing lambda is the
 * content-keyed [content] slot. The content-key, intro-callback, bounded-scroller, cleanup, and
 * placeholder CTA contracts are the same as the primary overload. `animate` and `entryDelay` are
 * sampled at intro start; settled tail and bounds use the latest `animate`.
 *
 * This composable must be called inside [OnboardingTheme]; it reads onboarding colours from that
 * provider, and omitting it causes the backing composition local to throw.
 *
 * Tail geometry is logical-direction relative: `None` is flat, `BottomStart` is 80dp from logical
 * start, `BottomStartWide` is 96dp, and `BottomEnd` is 80dp from logical end. `tailExtended = false`
 * retracts the tail depth; `true` fully extends it. `dismiss` is a 32dp affordance at logical
 * top/end with a 4dp visual overhang; hit and semantics bounds remain inside the layout.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1207908166761516/task/1219997332635983
 * Figma reference: https://www.figma.com/design/wMxBpe0mKrRS0nVhtwMGO7/%F0%9F%9A%80-Onboarding-Components--2026-?node-id=3005-41695
 *
 * @param contentKey Stable identity for the current content; change it to start a new intro.
 * @param title Title text; pass an empty string for the no-title choreography case.
 * @param body Composable body content, such as text containing inline content.
 * @param modifier Modifier applied to the dialog root.
 * @param state Hoisted state used to skip the current introduction.
 * @param animate Whether the next intro and settled tail/bounds changes animate.
 * @param entryDelay Extra delay added to the 200ms entrance delay.
 * @param textAlign Horizontal alignment for title and body text.
 * @param headerImage Optional image displayed above the title.
 * @param boundsAnimationDuration Duration used when the card bounds change.
 * @param tail Speech-bubble tail position.
 * @param tailExtended Whether the configured tail is fully extended.
 * @param primaryCta Optional primary CTA.
 * @param onIntroFinished Called after composition with the completed run’s content key; canceled runs do not call it.
 * @param dismiss Optional dismiss action and accessible content description.
 * @param step Optional step indicator centred on the painted top edge; `null` hides it.
 * @param content Optional content-keyed arbitrary content slot.
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

@SuppressLint("InvalidDaxTextColorUsage") // OnboardingTheme owns the binding-required content color.
private fun dialogBody(
    text: AnnotatedString,
    style: DuckDuckGoTextStyle,
    textAlign: DaxOnboardingDialogTextAlign,
): @Composable () -> Unit = {
    DaxText(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = style,
        color = DaxOnboardingDialogDefaults.ContentColor,
        textAlign = textAlign.toComposeTextAlign(),
    )
}

private fun DaxOnboardingDialogTextAlign.toComposeTextAlign(): TextAlign = when (this) {
    DaxOnboardingDialogTextAlign.Start -> TextAlign.Start
    DaxOnboardingDialogTextAlign.Center -> TextAlign.Center
}

@Composable
private fun OnboardingPreview(content: @Composable () -> Unit) {
    DuckDuckGoTheme {
        OnboardingTheme {
            PreviewBox(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
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
private fun DaxOnboardingDialogRtlPreview() {
    OnboardingPreview {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            DaxOnboardingDialog(
                contentKey = "centered-rtl",
                title = "RTL tail placement",
                body = "Start and end tails follow layout direction.",
                animate = false,
                tail = DaxOnboardingDialogTail.BottomEnd,
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
