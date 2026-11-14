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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.common.ui.compose.cards.DaxCard
import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.ProvideDuckDuckGoTheme
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.utils.extensions.preventWidows
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.milliseconds
import com.duckduckgo.mobile.android.R as CommonR

@Composable
internal fun QuickSetupContent(content: ContentConfig.QuickSetup, session: OnboardingContentState, enabled: Boolean) {
    val flow = session.stateFor(content)
    val state by flow.collectAsState()
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        if (!content.hideSetDefaultBrowserRow) {
            QuickSetupSwitch(
                CommonR.drawable.default_browser_mobile_color_24,
                stringResource(R.string.preOnboardingReinstallSetDefaultLabel),
                state.defaultBrowserChecked,
                enabled,
            ) { checked ->
                flow.update { it.copy(defaultBrowserChecked = checked) }
                session.interact(ContentInteraction.QuickSetupSetDefaultBrowser(checked))
            }
            DaxHorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        if (!content.hideAddWidgetRow) {
            QuickSetupSwitch(
                R.drawable.add_widget_color_24,
                stringResource(R.string.preOnboardingReinstallAddHomeScreenWidgetLabel),
                state.widgetChecked,
                enabled,
            ) { checked ->
                flow.update { it.copy(widgetChecked = checked) }
                session.interact(ContentInteraction.QuickSetupAddWidget(checked))
            }
            DaxHorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        QuickSetupEdit(
            icon = when (state.addressBarPosition) {
                OmnibarType.SINGLE_TOP -> R.drawable.ic_address_bar_top_24
                OmnibarType.SINGLE_BOTTOM -> R.drawable.ic_address_bar_bottom_24
                OmnibarType.SPLIT -> R.drawable.ic_address_bar_split_24
            },
            primary = stringResource(R.string.preOnboardingReinstallAddressBarLabel),
            secondary = stringResource(addressBarLabel(state.addressBarPosition)),
            enabled = enabled,
        ) { session.interact(ContentInteraction.QuickSetupEditAddressBarPosition) }
        if (!content.hideAddressBarRow) {
            DaxHorizontalDivider(Modifier.padding(horizontal = 16.dp))
            QuickSetupEdit(
                if (state.withAi) R.drawable.ic_ai_24 else R.drawable.ic_search_24,
                stringResource(R.string.preOnboardingReinstallAddressBarSearchOptionsLabel),
                stringResource(if (state.withAi) R.string.quickSetupInputScreenSearchAndDuckAi else R.string.quickSetupInputScreenSearchOnly),
                enabled,
            ) { session.interact(ContentInteraction.QuickSetupEditSearchOptions) }
        }
    }
}

@Composable
private fun QuickSetupSwitch(icon: Int, label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), null, Modifier.size(24.dp))
        DaxText(
            AnnotatedString(label, paragraphStyle = OnboardingBodyParagraphStyle),
            Modifier.weight(1f).padding(start = 16.dp, end = 8.dp),
            style = DuckDuckGoTheme.typography.onboardingBodyInContext,
        )
        OnboardingSwitch(checked)
    }
}

@Composable
private fun QuickSetupEdit(icon: Int, primary: String, secondary: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), null, Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            DaxText(
                AnnotatedString(primary, paragraphStyle = OnboardingBodyParagraphStyle),
                style = DuckDuckGoTheme.typography.onboardingBodyInContext,
            )
            DaxText(
                AnnotatedString(
                    secondary,
                    OnboardingLabelSpanStyle,
                    OnboardingLabelParagraphStyle.copy(platformStyle = PlatformParagraphStyle(includeFontPadding = false)),
                ),
                style = DuckDuckGoTheme.typography.onboardingBodyInContext,
                color = Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingAccentTextAlt)),
            )
        }
    }
}

@Composable
internal fun DownloadReasonContent(content: ContentConfig.DownloadReason, session: OnboardingContentState, enabled: Boolean) {
    val flow = session.stateFor(content)
    val state by flow.collectAsState()
    SideEffect { session.primaryEnabled = state.selection != null }
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        OnboardingBody(content.body.resolve(LocalContext.current).preventWidows())
        Column(Modifier.fillMaxWidth().padding(top = 20.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadReasonSelection.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { option ->
                        DownloadReasonTile(option, state.selection == option, enabled, Modifier.weight(1f).fillMaxHeight()) {
                            flow.value = DownloadReasonContentState(option)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadReasonTile(option: DownloadReasonSelection, selected: Boolean, enabled: Boolean, modifier: Modifier, select: () -> Unit) {
    val resource = when (option) {
        DownloadReasonSelection.SEARCH -> R.raw.onboarding_download_reason_search
        DownloadReasonSelection.AI_CHAT -> R.raw.onboarding_download_reason_ai_chat
        DownloadReasonSelection.NO_AI -> R.raw.onboarding_download_reason_no_ai
        DownloadReasonSelection.BLOCK_ADS -> R.raw.onboarding_download_reason_block_ads
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resource))
    val animation = rememberLottieAnimatable()
    var playRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(playRequest, composition) {
        val current = composition ?: return@LaunchedEffect
        if (playRequest == 0) return@LaunchedEffect
        delay(100)
        animation.animate(current, initialProgress = 0f)
        animation.snapTo(current, progress = 0f)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = selected || pressed
    val context = LocalContext.current
    val background = Color(context.getColorFromAttr(if (active) CommonR.attr.onboardingAccentAltPrimary else CommonR.attr.onboardingSurfaceTertiary))
    val stroke = Color(context.getColorFromAttr(if (active) CommonR.attr.onboardingAccentPrimary else CommonR.attr.onboardingTileStroke))
    ProvideDuckDuckGoTheme(colors = DuckDuckGoTheme.colors.copy(backgrounds = DuckDuckGoTheme.colors.backgrounds.copy(window = background))) {
        DaxCard(
            modifier.selectable(selected, interaction, indication = null, enabled = enabled, role = Role.RadioButton) {
                if (!selected && !animation.isPlaying) playRequest++
                select()
            },
            border = BorderStroke(
                dimensionResource(if (selected) R.dimen.brandDesignTabTileStrokeWidthSelected else R.dimen.brandDesignTabTileStrokeWidth),
                stroke,
            ),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.Horizontal { size, space, _ -> (space - size) / 2 },
            ) {
                LottieAnimation(composition, progress = { animation.progress }, modifier = Modifier.size(64.dp))
                DaxText(
                    AnnotatedString(stringResource(downloadReasonLabel(option)), paragraphStyle = OnboardingBodyParagraphStyle),
                    modifier = Modifier.fillMaxWidth(),
                    style = DuckDuckGoTheme.typography.onboardingBodyInContext,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

internal fun downloadReasonLabel(option: DownloadReasonSelection): Int = when (option) {
    DownloadReasonSelection.SEARCH -> R.string.downloadReasonOptionSearch
    DownloadReasonSelection.AI_CHAT -> R.string.downloadReasonOptionAiChat
    DownloadReasonSelection.NO_AI -> R.string.downloadReasonOptionNoAi
    DownloadReasonSelection.BLOCK_ADS -> R.string.downloadReasonOptionBlockAds
}

@Composable
internal fun PreferenceSelectorContent(content: ContentConfig.PreferenceSelector, session: OnboardingContentState, enabled: Boolean) {
    val flow = session.stateFor(content)
    val state by flow.collectAsState()
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        content.rows.forEachIndexed { index, row ->
            val visible = row.dependsOn?.let { state.enabled[it] != false } ?: true
            var shown by remember { mutableStateOf(visible) }
            val alpha = remember { Animatable(if (visible) 1f else 0f) }
            LaunchedEffect(visible) {
                session.boundsDuration = 400.milliseconds
                if (visible) {
                    shown = true
                    alpha.animateTo(1f, tween(200, easing = OnboardingFadeEasing))
                } else {
                    alpha.animateTo(0f, tween(200, easing = OnboardingFadeEasing))
                    shown = false
                }
            }
            if (shown) {
                val interactive = enabled && visible && alpha.value == 1f
                Column(
                    Modifier.fillMaxWidth().padding(top = if (index == 0) 0.dp else 16.dp).padding(horizontal = 4.dp)
                        .graphicsLayer { this.alpha = alpha.value }
                        .then(if (interactive) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = dimensionResource(CommonR.dimen.twoLineItemHeight))
                            .toggleable(state.enabled.getValue(row.preference), enabled = interactive, role = Role.Switch) { checked ->
                                flow.update { it.copy(enabled = it.enabled + (row.preference to checked)) }
                            }.padding(vertical = dimensionResource(R.dimen.quickSetupTwoLineRowVerticalPadding)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.iconRes?.let { Image(painterResource(it), null, Modifier.size(24.dp)) }
                        Column(Modifier.weight(1f).padding(start = 10.dp, end = 20.dp)) {
                            DaxText(
                                AnnotatedString(
                                    row.primaryText.resolve(LocalContext.current),
                                    SpanStyle(fontSize = nativeOnboardingTextSize(16.sp)),
                                    OnboardingBodyParagraphStyle.copy(lineHeight = nativeOnboardingLineHeight(16.sp)),
                                ),
                                style = DuckDuckGoTheme.typography.onboardingBodyInContext,
                            )
                            row.secondaryText?.let {
                                DaxText(
                                    AnnotatedString(
                                        it.resolve(LocalContext.current),
                                        SpanStyle(fontSize = nativeOnboardingTextSize(14.sp)),
                                        OnboardingBodyParagraphStyle.copy(lineHeight = nativeOnboardingLineHeight(18.sp)),
                                    ),
                                    style = DuckDuckGoTheme.typography.onboardingBodyInContext,
                                    color = Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingTextSecondary)),
                                )
                            }
                        }
                        OnboardingSwitch(state.enabled.getValue(row.preference))
                    }
                    DaxHorizontalDivider(Modifier.padding(top = 16.dp))
                }
            }
        }
        content.caption?.let {
            DaxText(
                AnnotatedString(
                    it.resolve(LocalContext.current),
                    SpanStyle(fontSize = nativeOnboardingTextSize(14.sp)),
                    ParagraphStyle(lineHeight = nativeOnboardingLineHeight(16.sp)),
                ),
                Modifier.padding(top = 16.dp),
                style = DuckDuckGoTheme.typography.onboardingBodyInContext,
                color = Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingTextSecondarySmall)),
            )
        }
    }
}

@Composable
internal fun SingleChoiceContent(content: ContentConfig.SingleChoice, session: OnboardingContentState, enabled: Boolean) {
    val flow = session.stateFor(content)
    val state by flow.collectAsState()
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        OnboardingBody(content.body.resolve(LocalContext.current).preventWidows())
        Column(Modifier.fillMaxWidth().padding(top = 20.dp).padding(4.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content.rows.forEach { option ->
                val selected = state.selected == option
                val context = LocalContext.current
                val fill = if (selected) Color(context.getColorFromAttr(CommonR.attr.onboardingAccentGlowPrimary)) else Color.Transparent
                val borderAttribute = if (selected) CommonR.attr.onboardingAccentPrimary else CommonR.attr.onboardingBorderTertiary
                Row(
                    Modifier.fillMaxWidth()
                        .background(fill, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(context.getColorFromAttr(borderAttribute)), RoundedCornerShape(16.dp))
                        .selectable(selected, enabled = enabled, role = Role.RadioButton) { flow.update { it.copy(selected = option) } }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    option.iconRes?.let { Image(painterResource(it), null, Modifier.size(24.dp)) }
                    DaxText(
                        AnnotatedString(
                            option.label,
                            paragraphStyle = ParagraphStyle(platformStyle = PlatformParagraphStyle(includeFontPadding = true)),
                        ),
                        Modifier.weight(1f).padding(start = if (option.iconRes == null) 0.dp else 12.dp, end = 16.dp),
                        style = DuckDuckGoTheme.typography.body1,
                    )
                    OnboardingRadioButton(selected)
                }
            }
        }
    }
}

@Composable
internal fun OnboardingRadioButton(selected: Boolean, modifier: Modifier = Modifier) {
    val attribute = if (selected) CommonR.attr.onboardingAccentPrimary else CommonR.attr.onboardingIconsTertiary
    OnboardingRadioDrawable(selected, LocalContext.current.getColorFromAttr(attribute), modifier)
}

@Composable
internal fun OnboardingSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    OnboardingSwitchDrawable(checked, modifier)
}
