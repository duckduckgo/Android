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

package com.duckduckgo.app.onboarding.ui.page.welcome

import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.utils.extensions.preventWidows
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import com.duckduckgo.mobile.android.R as CommonR

@Composable
internal fun WidgetPromptContent(content: ContentConfig.WidgetPrompt) {
    val preview = painterResource(R.drawable.onboarding_widget_prompt_preview)
    val density = LocalDensity.current
    val maxImageWidth = dimensionResource(R.dimen.preOnboardingWidgetPromptMediaMaxWidth)
    val mediaWidth = with(density) {
        val imageWidth = minOf(preview.intrinsicSize.width.toInt(), maxImageWidth.roundToPx())
        (imageWidth + 2 * 4.dp.roundToPx() + 2 * 8.dp.roundToPx()).toDp()
    }
    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.Horizontal { size, space, _ -> (space - size) / 2 }) {
        OnboardingBody(content.body.resolve(LocalContext.current).preventWidows(), Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(32.dp))
        Column(Modifier.widthIn(max = mediaWidth).testTag("widget-media")) {
            Box(Modifier.padding(horizontal = 8.dp)) {
                Image(
                    painterResource(R.drawable.onboarding_widget_prompt_sketch_frame),
                    null,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.FillBounds,
                )
                Image(
                    preview,
                    null,
                    Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp).fillMaxWidth()
                        .layout { measurable, constraints ->
                            val height = (constraints.maxWidth / 2.4).roundToInt()
                            val image = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                            layout(image.width, image.height) { image.place(0, 0) }
                        }.testTag("widget-preview"),
                    contentScale = ContentScale.FillBounds,
                )
            }
            DaxHorizontalDivider(color = Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingAccentPrimary)))
        }
    }
}

@Composable
internal fun AddressBarContent(content: ContentConfig.AddressBar, session: OnboardingContentState, enabled: Boolean) {
    val flow = session.stateFor(content)
    val state by flow.collectAsState()
    val options = if (content.showSplitOption) OmnibarType.entries else listOf(OmnibarType.SINGLE_TOP, OmnibarType.SINGLE_BOTTOM)
    val light = !DuckDuckGoTheme.colors.isDark
    PackedAddressBarOptions(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp).selectableGroup()) {
        options.forEach { option ->
            val selected = state.position == option
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .selectable(selected, interaction, indication = null, enabled = enabled, role = Role.RadioButton) {
                        flow.update { it.copy(position = option) }
                    }
                    .padding(8.dp),
                horizontalAlignment = Alignment.Horizontal { size, space, _ -> (space - size) / 2 },
            ) {
                OnboardingImageRipple(interaction, dimensionResource(CommonR.dimen.smallShapeCornerRadius)) {
                    Crossfade(
                        addressBarPreview(option, selected, light),
                        animationSpec = tween(200, easing = OnboardingFadeEasing),
                        label = "address preview",
                    ) { resource ->
                        Image(painterResource(resource), null, Modifier.widthIn(max = 60.dp).testTag("address-preview-$option"))
                    }
                }
                OnboardingChoiceLabel(stringResource(addressBarLabel(option)), Modifier.padding(top = 6.dp))
                OnboardingRadioButton(selected, Modifier.padding(top = 6.dp))
            }
        }
    }
}

internal fun addressBarLabel(option: OmnibarType): Int = when (option) {
    OmnibarType.SINGLE_TOP -> R.string.preOnboardingAddressBarPositionTop
    OmnibarType.SINGLE_BOTTOM -> R.string.preOnboardingAddressBarPositionBottom
    OmnibarType.SPLIT -> R.string.preOnboardingAddressBarPositionSplit
}

private fun addressBarPreview(option: OmnibarType, active: Boolean, light: Boolean): Int = when (option) {
    OmnibarType.SINGLE_TOP -> when {
        active && light -> R.drawable.mobile_toolbar_top_selected_brand_design_update_light
        active -> R.drawable.mobile_toolbar_top_selected_brand_design_update_dark
        light -> R.drawable.mobile_toolbar_top_unselected_brand_design_update_light
        else -> R.drawable.mobile_toolbar_top_unselected_brand_design_update_dark
    }
    OmnibarType.SINGLE_BOTTOM -> when {
        active && light -> R.drawable.mobile_toolbar_bottom_selected_brand_design_update_light
        active -> R.drawable.mobile_toolbar_bottom_selected_brand_design_update_dark
        light -> R.drawable.mobile_toolbar_bottom_unselected_brand_design_update_light
        else -> R.drawable.mobile_toolbar_bottom_unselected_brand_design_update_dark
    }
    OmnibarType.SPLIT -> when {
        active && light -> R.drawable.mobile_toolbar_split_selected_brand_design_update_light
        active -> R.drawable.mobile_toolbar_split_selected_brand_design_update_dark
        light -> R.drawable.mobile_toolbar_split_unselected_brand_design_update_light
        else -> R.drawable.mobile_toolbar_split_unselected_brand_design_update_dark
    }
}

// The View pickers make the whole option clickable but ripple only the artwork, via a foreground on its frame.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingImageRipple(interaction: MutableInteractionSource, cornerRadius: Dp, content: @Composable () -> Unit) {
    val highlight = Color(LocalContext.current.getColorFromAttr(android.R.attr.colorControlHighlight))
    CompositionLocalProvider(LocalRippleConfiguration provides rememberViewRippleConfiguration(highlight)) {
        Box {
            content()
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(cornerRadius)).indication(interaction, ripple()))
        }
    }
}

/** A View RippleDrawable paints its colour as given, alpha included, rather than at Material's state alphas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberViewRippleConfiguration(color: Color): RippleConfiguration = remember(color) {
    val alpha = color.alpha
    RippleConfiguration(color.copy(alpha = 1f), RippleAlpha(alpha, alpha, alpha, alpha))
}

@Composable
internal fun OnboardingChoiceLabel(text: String, modifier: Modifier = Modifier) {
    DaxText(
        AnnotatedString(text, OnboardingLabelSpanStyle, OnboardingLabelParagraphStyle),
        modifier,
        style = DuckDuckGoTheme.typography.onboardingBodyInContext,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun InputScreenContent(content: ContentConfig.InputScreen, session: OnboardingContentState, enabled: Boolean) {
    val flow = session.stateFor(content)
    val state by flow.collectAsState()
    val light = !DuckDuckGoTheme.colors.isDark
    Column(Modifier.fillMaxWidth().padding(top = 32.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false, true).forEach { withAi ->
                val interaction = remember { MutableInteractionSource() }
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .selectable(state.withAi == withAi, interaction, indication = null, enabled = enabled, role = Role.RadioButton) {
                            flow.update { it.copy(withAi = withAi) }
                        },
                    horizontalAlignment = Alignment.Horizontal { size, space, _ -> (space - size) / 2 },
                ) {
                    OnboardingImageRipple(interaction, dimensionResource(CommonR.dimen.extraLargeShapeCornerRadius)) {
                        if (withAi) {
                            InputScreenAnimation(state.withAi, light, session)
                        } else {
                            val resource = when {
                                state.withAi && light -> CommonR.drawable.searchbox_withoutai_inactive_brand_design_update
                                state.withAi -> CommonR.drawable.searchbox_withoutai_inactive_dark_brand_design_update
                                light -> CommonR.drawable.searchbox_withoutai_active_brand_design_update
                                else -> CommonR.drawable.searchbox_withoutai_active_dark_brand_design_update
                            }
                            Crossfade(resource, animationSpec = tween(200, easing = OnboardingFadeEasing), label = "search preview") { res ->
                                Image(painterResource(res), null, Modifier.size(128.dp, 80.dp), contentScale = ContentScale.FillBounds)
                            }
                        }
                    }
                    OnboardingChoiceLabel(
                        stringResource(
                            if (withAi) {
                                R.string.input_screen_user_pref_with_ai_updated
                            } else {
                                R.string.input_screen_user_pref_without_ai_updated
                            },
                        ),
                        Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    OnboardingRadioButton(state.withAi == withAi, Modifier.padding(top = 6.dp).testTag("input-screen-radio-$withAi"))
                }
            }
        }
        val description = content.description.resolve(LocalContext.current).preventWidows()
        DaxText(
            AnnotatedString.Builder(AnnotatedString.fromHtml(description)).apply {
                addStyle(OnboardingBodyParagraphStyle, 0, length)
                addStyle(SpanStyle(fontFamily = OnboardingBodyFontFamily), 0, length)
            }.toAnnotatedString(),
            Modifier.padding(top = 16.dp),
            style = DuckDuckGoTheme.typography.onboardingBodyInContext,
            color = Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingTextSecondary)),
        )
    }
}

@Composable
private fun InputScreenAnimation(withAi: Boolean, light: Boolean, session: OnboardingContentState) {
    val resource = when {
        withAi && light -> CommonR.raw.searchbox_with_ai_active
        withAi -> CommonR.raw.searchbox_with_ai_active_dark
        light -> CommonR.raw.searchbox_with_ai_inactive
        else -> CommonR.raw.searchbox_with_ai_inactive_dark
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resource))
    val animation = rememberLottieAnimatable()
    var pendingEntranceResource by remember { mutableStateOf<Int?>(resource) }
    LaunchedEffect(composition, session.readyAnimated) {
        val current = composition ?: return@LaunchedEffect
        if (session.readyAnimated == null || session.disposed) return@LaunchedEffect
        val first = pendingEntranceResource == resource
        if (first) delay(2000)
        pendingEntranceResource = null
        animation.animate(current, initialProgress = if (first) 0f else animation.progress)
        while (isActive) {
            delay(2000)
            animation.animate(current, initialProgress = 0f)
        }
    }
    Crossfade(
        composition,
        modifier = Modifier.testTag("input-ai-preview"),
        animationSpec = tween(200, easing = OnboardingFadeEasing),
        label = "AI preview",
    ) { current ->
        LottieAnimation(
            current,
            progress = { animation.progress },
            modifier = Modifier.size(128.dp, 80.dp),
            contentScale = ContentScale.FillBounds,
        )
    }
}

internal val OnboardingFadeEasing = Easing(AccelerateDecelerateInterpolator()::getInterpolation)

@Composable
private fun PackedAddressBarOptions(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val itemMaxWidth = minOf(160.dp.roundToPx(), constraints.maxWidth / measurables.size)
        val children = measurables.map { it.measure(constraints.copy(minWidth = 0, maxWidth = itemMaxWidth, minHeight = 0)) }
        val gap = minOf(56.dp.roundToPx().toFloat(), (constraints.maxWidth - children.sumOf { it.width }).toFloat() / (children.size - 1))
        val width = children.sumOf { it.width } + gap * (children.size - 1)
        val height = children.maxOf { it.height }
        layout(constraints.maxWidth, height) {
            var x = (constraints.maxWidth - width) / 2
            children.forEach {
                it.placeRelative(x.roundToInt(), (height - it.height) / 2)
                x += it.width + gap
            }
        }
    }
}
