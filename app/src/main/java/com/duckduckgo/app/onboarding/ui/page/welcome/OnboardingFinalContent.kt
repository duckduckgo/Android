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

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingPrimaryButton
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingSecondaryButton
import com.duckduckgo.common.ui.compose.skeleton.DaxSkeletonListItem
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.utils.extensions.preventWidows
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import kotlinx.coroutines.flow.collectLatest
import com.duckduckgo.mobile.android.R as CommonR

@Composable
internal fun onboardingHeaderImage(session: OnboardingContentState): Painter? {
    val resource = when (session.config) {
        is ContentConfig.DuckAiState -> CommonR.drawable.duckduckgo_duckai_96
        is ContentConfig.ImportPasswords -> R.drawable.ic_passwords_import_96
        is ContentConfig.ImportComplete -> when (session.displayedImportOutcome) {
            is ImportCompleteContentState.Finished -> R.drawable.ic_success_96
            ImportCompleteContentState.Failed -> R.drawable.ic_passwords_alert_96
            else -> R.drawable.ic_passwords_import_96
        }
        else -> return null
    }
    val painter = painterResource(resource)
    return remember(painter, session) {
        object : Painter() {
            override val intrinsicSize get() = painter.intrinsicSize
            override fun DrawScope.onDraw() {
                with(painter) { draw(size, alpha = session.importAlpha.value) }
            }
        }
    }
}

@Composable
internal fun DuckAiStateContent(content: ContentConfig.DuckAiState, session: OnboardingContentState, enabled: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        OnboardingBody(content.body.resolve(LocalContext.current).preventWidows())
        OnboardingOptionButtons(content.options, session, enabled, Modifier.padding(top = 20.dp))
    }
}

@Composable
internal fun TogglePositionContent(content: ContentConfig.TogglePosition, session: OnboardingContentState, enabled: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(if (DuckDuckGoTheme.colors.isDark) content.pictogramDarkRes else content.pictogramLightRes),
            null,
            Modifier.size(128.dp, 80.dp).testTag("toggle-position-preview"),
        )
        DaxText(
            AnnotatedString(
                content.pictogramCaption.resolve(LocalContext.current).preventWidows(),
                SpanStyle(fontSize = nativeOnboardingTextSize(14.sp)),
                ParagraphStyle(lineHeight = nativeOnboardingLineHeight(16.sp, 18.sp)),
            ),
            Modifier.padding(top = 24.dp),
            style = DuckDuckGoTheme.typography.onboardingBody,
            color = Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingTextSecondarySmall)),
            textAlign = TextAlign.Center,
        )
        OnboardingOptionButtons(content.options, session, enabled, Modifier.padding(top = 20.dp))
    }
}

@Composable
internal fun ImportPasswordsContent(content: ContentConfig.ImportPasswords) {
    OnboardingBody(
        content.body.resolve(LocalContext.current).preventWidows(),
        Modifier.fillMaxWidth().padding(top = 20.dp, start = 12.dp, end = 12.dp),
    )
}

@Composable
private fun OnboardingOptionButtons(options: List<Option>, session: OnboardingContentState, enabled: Boolean, modifier: Modifier) {
    val gap = with(LocalDensity.current) { (2 * 4.dp.roundToPx()).toDp() }
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        options.forEachIndexed { index, option ->
            val onClick = { session.interact(ContentInteraction.SelectSingleChoiceOption(option)) }
            if (index == 0) {
                DaxOnboardingPrimaryButton(
                    option.label,
                    onClick,
                    Modifier.fillMaxWidth(),
                    enabled = enabled,
                )
            } else {
                DaxOnboardingSecondaryButton(option.label, onClick, Modifier.fillMaxWidth(), enabled)
            }
        }
    }
}

@Composable
internal fun ImportCompleteContent(content: ContentConfig.ImportComplete, session: OnboardingContentState) {
    LaunchedEffect(session, session.readyAnimated) {
        if (session.readyAnimated == null) return@LaunchedEffect
        session.stateFor(content).collectLatest { incoming ->
            if (session.disposed) return@collectLatest
            if (incoming != session.displayedImportOutcome) {
                session.importAlpha.animateTo(0f, tween(150, easing = OnboardingFadeEasing))
                if (session.disposed) return@collectLatest
                session.presentImportOutcome(incoming)
            }
            session.importAlpha.animateTo(1f, tween(150, easing = OnboardingFadeEasing))
        }
    }
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = session.importAlpha.value }.testTag("import-outcome")) {
        when (val outcome = session.displayedImportOutcome) {
            ImportCompleteContentState.Parsing -> {
                DaxText(
                    AnnotatedString(
                        content.parsingBody.resolve(LocalContext.current),
                        SpanStyle(fontSize = nativeOnboardingTextSize(14.sp), letterSpacing = 0.sp),
                        OnboardingBodyParagraphStyle.copy(lineHeight = nativeOnboardingLineHeight(19.sp)),
                    ),
                    Modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp, end = 12.dp),
                    style = DuckDuckGoTheme.typography.onboardingBodyInContext,
                    color = Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingTextSecondary)),
                    textAlign = TextAlign.Center,
                )
                DaxSkeletonListItem(
                    Modifier.padding(top = 20.dp, end = 16.dp).height(40.dp).testTag("import-parsing"),
                    leadingColor = Color(LocalContext.current.getColorFromAttr(CommonR.attr.daxColorContainer)),
                )
            }
            is ImportCompleteContentState.Finished -> ImportResultContainer {
                ImportResultRow(CommonR.drawable.check_recolorable_24, importCount(R.string.preOnboardingImportCompleteImported, outcome.imported))
                if (outcome.skipped > 0) {
                    ImportResultRow(
                        CommonR.drawable.ic_cross_recolorable_gray_24,
                        importCount(R.string.preOnboardingImportCompleteSkipped, outcome.skipped),
                    )
                }
            }
            ImportCompleteContentState.Failed -> ImportResultContainer {
                ImportResultRow(CommonR.drawable.cross_recolorable_24, AnnotatedString(content.failedRow.resolve(LocalContext.current)))
            }
            null -> Unit
        }
    }
}

@Composable
private fun ImportResultContainer(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp)
            .background(
                Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingAccentAltGlowPrimary)),
                RoundedCornerShape(dimensionResource(CommonR.dimen.largeShapeCornerRadius)),
            ),
        content = { content() },
    )
}

@Composable
private fun ImportResultRow(icon: Int, text: AnnotatedString) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = dimensionResource(CommonR.dimen.oneLineItemHeight)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.Vertical { size, space -> (space - size) / 2 },
    ) {
        Image(painterResource(icon), null, Modifier.size(24.dp))
        DaxText(
            AnnotatedString.Builder(text).apply { addStyle(OnboardingBodyParagraphStyle, 0, length) }.toAnnotatedString(),
            Modifier.padding(start = 12.dp),
            style = DuckDuckGoTheme.typography.onboardingBodyInContext,
        )
    }
}

@Composable
private fun importCount(resource: Int, count: Int): AnnotatedString {
    val value = count.toString()
    val text = stringResource(resource, value)
    val start = text.indexOf(value)
    return AnnotatedString.Builder(text).apply {
        if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + value.length)
    }.toAnnotatedString()
}
