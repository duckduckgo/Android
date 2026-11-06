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

import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import android.view.ContextThemeWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.button.DaxIconButtonDefaults
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import com.duckduckgo.browser.ui.R as BrowserUiR
import com.duckduckgo.mobile.android.R as CommonR

// Changing the output transformation also invalidates Foundation's cached single-line display when switching modes.
private val SearchPreviewOutputTransformation = OutputTransformation {
    for (index in 0 until length) {
        when (charAt(index)) {
            '\n' -> replace(index, index + 1, " ")
            '\r' -> replace(index, index + 1, "\uFEFF")
        }
    }
}

@Composable
internal fun InputScreenPreviewContent(content: ContentConfig.InputScreenPreview, session: OnboardingContentState, enabled: Boolean) {
    val flow = session.stateFor(content)
    val mode by flow.collectAsState()
    val editor = session.inputEditor
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val interactions = remember { MutableInteractionSource() }
    var inputActivated by remember { mutableStateOf(false) }
    LaunchedEffect(interactions, enabled) {
        interactions.interactions.collect {
            if (enabled && it is PressInteraction.Release) inputActivated = true
        }
    }
    val density = LocalDensity.current
    val inputFontSize = nativeOnboardingTextSize(16.sp)
    val inputLineHeight = remember(density, inputFontSize) {
        val paint = Paint().apply {
            textSize = with(density) { inputFontSize.toPx() }
            typeface = Typeface.SANS_SERIF
        }
        with(density) {
            if (fontScale == 1f) paint.getFontMetricsInt(null).toSp() else (paint.getFontMetricsInt(null) / paint.textSize).em
        }
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val ready = session.readyAnimated
    val suggestions = if (mode.isSearchSelected) content.searchSuggestions else content.chatSuggestions
    val fades = remember { List(3) { Animatable(0f) } }
    var suggestionsEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(ready, session.skipped) {
        if (ready == null) return@LaunchedEffect
        session.boundsDuration = 500.milliseconds
        if (!ready || session.skipped) {
            fades.forEach { it.snapTo(1f) }
        } else {
            delay(500)
            coroutineScope {
                fades.forEachIndexed { index, fade ->
                    launch {
                        delay(index * 500L)
                        fade.animateTo(1f, tween(500, easing = OnboardingFadeEasing))
                    }
                }
            }
        }
        suggestionsEnabled = true
    }
    LaunchedEffect(ready) {
        if (ready != null && screenHeight >= 600 && !session.disposed) {
            inputActivated = true
            focus.requestFocus()
        }
    }
    LaunchedEffect(mode.isSearchSelected) { session.boundsDuration = Duration.ZERO }
    val fieldHeight = remember { Animatable(0f) }
    var naturalFieldHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(fieldHeight) {
        snapshotFlow { naturalFieldHeight }.collectLatest { target ->
            if (fieldHeight.value == 0f) {
                fieldHeight.snapTo(
                    target.toFloat(),
                )
            } else {
                fieldHeight.animateTo(target.toFloat(), tween(400, easing = OnboardingFadeEasing))
            }
        }
    }
    val submit = {
        val query = editor.text.toString().trim()
        if (enabled && query.isNotEmpty()) {
            session.interact(ContentInteraction.SubmitInputPreview(query, !mode.isSearchSelected, false))
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Horizontal { size, space, _ -> (space - size) / 2 }) {
        if (content.showModeToggle) {
            InputPreviewModeToggle(
                mode.isSearchSelected,
                enabled,
                Modifier.padding(top = with(density) { (2 * 8.dp.roundToPx()).toDp() }),
            ) { search ->
                flow.update { it.copy(isSearchSelected = search) }
            }
        }
        val radius = if (session.addressBarRebrandEnabled) {
            dimensionResource(CommonR.dimen.rebrandInputRadius) / 2
        } else {
            dimensionResource(CommonR.dimen.largeShapeCornerRadius)
        }
        val shape = RoundedCornerShape(radius)
        val hint =
            stringResource(if (mode.isSearchSelected) R.string.preOnboardingInputModeDemoSearchHint else R.string.preOnboardingInputModeDemoChatHint)
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().onboardingBottomShadow(radius)
                .background(Color(LocalContext.current.getColorFromAttr(CommonR.attr.daxColorWindow)), shape)
                .testTag("input-preview-card").border(1.dp, DuckDuckGoTheme.colors.system.lines, shape).padding(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                state = editor,
                modifier = Modifier.weight(1f)
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        naturalFieldHeight = placeable.height
                        val height = if (fieldHeight.value == 0f) placeable.height else fieldHeight.value.roundToInt()
                        layout(placeable.width, constraints.constrainHeight(height)) { placeable.place(0, 0) }
                    }
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        val editingKey = (event.nativeKeyEvent.unicodeChar != 0 && event.key != Key.Tab && event.key != Key.Escape) ||
                            event.key == Key.Backspace || event.key == Key.Delete || event.key == Key.Enter
                        if (enabled && event.type == KeyEventType.KeyDown && editingKey) inputActivated = true
                        false
                    }
                    .semantics {
                        onClick {
                            if (enabled) {
                                inputActivated = true
                                focus.requestFocus()
                                keyboard?.show()
                            }
                            enabled
                        }
                    }
                    .heightIn(min = dimensionResource(CommonR.dimen.toolbarIcon))
                    .padding(
                        start = if (session.addressBarRebrandEnabled) 14.dp else 10.dp,
                        top = if (session.addressBarRebrandEnabled) 10.dp else 8.dp,
                        bottom = if (session.addressBarRebrandEnabled) 10.dp else 8.dp,
                    ),
                enabled = enabled,
                textStyle = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = inputFontSize,
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                    color = Color(LocalContext.current.getColorFromAttr(CommonR.attr.daxColorPrimaryText)),
                ),
                cursorBrush = SolidColor(DuckDuckGoTheme.colors.brand.accentBlue),
                interactionSource = interactions,
                keyboardOptions = KeyboardOptions(
                    imeAction = if (mode.isSearchSelected) ImeAction.Search else ImeAction.Default,
                    showKeyboardOnFocus = inputActivated,
                ),
                onKeyboardAction = if (mode.isSearchSelected) { { submit() } } else null,
                outputTransformation = if (mode.isSearchSelected) SearchPreviewOutputTransformation else null,
                lineLimits = if (mode.isSearchSelected) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minHeightInLines = 3),
                decorator = { inner ->
                    Box {
                        if (editor.text.isEmpty()) {
                            DaxText(
                                AnnotatedString(
                                    hint,
                                    SpanStyle(fontFamily = FontFamily.SansSerif, fontSize = inputFontSize),
                                    ParagraphStyle(lineHeight = inputLineHeight, platformStyle = PlatformParagraphStyle(includeFontPadding = true)),
                                ),
                                style = DuckDuckGoTheme.typography.body1,
                                color = DuckDuckGoTheme.textColors.secondary,
                            )
                        }
                        inner()
                    }
                },
            )
            DaxIconButton(
                onClick = submit,
                iconPainter = painterResource(if (mode.isSearchSelected) CommonR.drawable.ic_find_search_24 else CommonR.drawable.ic_arrow_right_24),
                contentDescription = hint,
                enabled = enabled,
                modifier = Modifier.padding(end = if (session.addressBarRebrandEnabled) 6.dp else 0.dp)
                    .size(dimensionResource(CommonR.dimen.toolbarIcon)),
                iconSize = 24.dp,
                colors = DaxIconButtonDefaults.iconButtonColors.copy(
                    contentColor = Color(LocalContext.current.getColorFromAttr(CommonR.attr.daxColorSecondaryIcon)),
                ),
            )
        }
        if (ready != null) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                suggestions.take(3).forEachIndexed { index, suggestion ->
                    val interactive = enabled && suggestionsEnabled
                    OnboardingSelectionButton(
                        text = suggestion.optionText,
                        iconRes = suggestion.iconRes,
                        enabled = interactive,
                        modifier = Modifier.graphicsLayer { alpha = fades[index].value }
                            .then(if (interactive) Modifier else Modifier.clearAndSetSemantics {}),
                    ) {
                        if (interactive) session.interact(ContentInteraction.SubmitInputPreview(suggestion.link, !mode.isSearchSelected, true))
                    }
                }
            }
        }
    }
}

@Composable
private fun InputPreviewModeToggle(search: Boolean, enabled: Boolean, modifier: Modifier, onSelect: (Boolean) -> Unit) {
    val context = LocalContext.current
    val indicator = Color(context.getColorFromAttr(CommonR.attr.daxColorInputModeIndicator))
    val shadow = Color(context.getColorFromAttr(CommonR.attr.daxColorInputModeIndicatorShadow))
    val foreground = Color(context.getColorFromAttr(CommonR.attr.daxColorPrimaryText))
    val density = LocalDensity.current
    val fontSize = nativeOnboardingTextSize(14.sp)
    val lineHeight = remember(density, fontSize) {
        val paint = Paint().apply {
            textSize = with(density) { fontSize.toPx() }
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        with(density) {
            if (fontScale == 1f) paint.getFontMetricsInt(null).toSp() else (paint.getFontMetricsInt(null) / paint.textSize).em
        }
    }
    val pill = remember(context, indicator, shadow) {
        val radius = context.resources.getDimensionPixelSize(BrowserUiR.dimen.inputModeTabIndicatorHeight) / 2f
        MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, radius).build()).apply {
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_ALWAYS
            initializeElevationOverlay(context)
            setShadowColor(shadow.toArgb())
            elevation = context.resources.getDimensionPixelSize(BrowserUiR.dimen.inputModeTabIndicatorElevation).toFloat()
            setTint(indicator.toArgb())
        }
    }
    val position by animateFloatAsState(if (search) 0f else 1f, tween(300), label = "input mode indicator")
    Layout(
        modifier = modifier.clip(RoundedCornerShape(19.dp))
            .background(Color(context.getColorFromAttr(CommonR.attr.daxColorBackdrop))).selectableGroup().testTag("input-mode-toggle"),
        content = {
            Box(
                Modifier.drawBehind {
                    pill.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                    pill.draw(drawContext.canvas.nativeCanvas)
                }.testTag("input-mode-indicator"),
            )
            listOf(true, false).forEach { isSearch ->
                Row(
                    Modifier.selectable(search == isSearch, enabled = enabled, role = Role.Tab) { onSelect(isSearch) }
                        .padding(start = 12.dp, end = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painterResource(if (isSearch) CommonR.drawable.ic_find_search_16 else BrowserUiR.drawable.ic_input_screen_ai_chat_16),
                        null,
                        Modifier.padding(start = 2.dp).size(16.dp),
                    )
                    DaxText(
                        AnnotatedString(
                            stringResource(if (isSearch) BrowserUiR.string.input_mode_search_tab else BrowserUiR.string.input_mode_chat_tab),
                            SpanStyle(fontFamily = FontFamily.SansSerif, fontSize = fontSize, fontWeight = FontWeight.Medium),
                            ParagraphStyle(lineHeight = lineHeight, platformStyle = PlatformParagraphStyle(includeFontPadding = true)),
                        ),
                        style = DuckDuckGoTheme.typography.h4,
                        color = foreground,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val height = 36.dp.roundToPx()
        val tabs = measurables.drop(1).map { it.measure(Constraints(maxWidth = constraints.maxWidth / 2, minHeight = height, maxHeight = height)) }
        val inset = 2.dp.roundToPx()
        val pillWidth = (tabs[0].width + (tabs[1].width - tabs[0].width) * position).roundToInt() - inset * 2
        val pill = measurables.first().measure(Constraints.fixed(pillWidth.coerceAtLeast(0), height - inset * 2))
        layout(tabs.sumOf { it.width }, height) {
            pill.placeRelative(inset + (tabs[0].width * position).roundToInt(), inset)
            tabs[0].placeRelative(0, 0)
            tabs[1].placeRelative(tabs[0].width, 0)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingSelectionButton(text: String, iconRes: Int?, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val nightMode = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val themedContext = remember(context, nightMode) {
        ContextThemeWrapper(
            context,
            if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                CommonR.style.Theme_DuckDuckGo_Dark_Onboarding
            } else {
                CommonR.style.Theme_DuckDuckGo_Light_Onboarding
            },
        )
    }
    val accent = Color(themedContext.getColorFromAttr(CommonR.attr.onboardingAccentTextPrimary))
    val ripple = rememberViewRippleConfiguration(Color(themedContext.getColorFromAttr(CommonR.attr.onboardingAccentAltGlowPrimary)))
    CompositionLocalProvider(LocalRippleConfiguration provides ripple) {
        Row(
            modifier.fillMaxWidth().heightIn(min = 48.dp)
                .padding(vertical = 4.dp).clip(RoundedCornerShape(50))
                .clickable(interactionSource = null, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
                .border(1.dp, Color(themedContext.getColorFromAttr(CommonR.attr.onboardingAccentPrimary)), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconRes != null) Image(painterResource(iconRes), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(accent))
            DaxText(
                AnnotatedString(text, SpanStyle(fontSize = nativeOnboardingTextSize(15.sp), fontWeight = FontWeight.Normal)),
                style = DuckDuckGoTheme.typography.onboardingBodyInContext,
                color = accent,
            )
        }
    }
}
