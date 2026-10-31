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

import android.os.Build
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.utils.extensions.preventWidows
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.duckduckgo.mobile.android.R as CommonR

@Composable
internal fun WelcomeContent(content: ContentConfig.Welcome) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(0.dp))
        OnboardingBody(content.body1.resolve(context).preventWidows())
        content.body2?.let { OnboardingBody(it.resolve(context).preventWidows()) }
    }
}

@Composable
internal fun OnboardingBody(text: String, modifier: Modifier = Modifier) {
    DaxText(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = DuckDuckGoTheme.typography.onboardingBody,
        color = Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingTextPrimary)),
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun ComparisonChartContent(config: ComparisonChartConfig, readyAnimated: Boolean?, skipped: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            config.headerLeftLabelRes?.let {
                DaxText(
                    stringResource(it).preventWidows(), Modifier.weight(1f).padding(end = 8.dp),
                    style = DuckDuckGoTheme.typography.body2, textAlign = TextAlign.End,
                    color = Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingTextPrimary)),
                )
            } ?: Spacer(Modifier.weight(1f))
            ChartHeaderIcon(config.headerLeftIconRes, config.headerLeftIconSizeDp, Color.White)
            Spacer(Modifier.width(8.dp))
            ChartHeaderIcon(CommonR.drawable.ddg_logo_full_bleed, 31.5f, Color(0xFFDE5833))
        }
        config.rows.forEachIndexed { index, row ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).height(IntrinsicSize.Min).testTag("comparison-row-$index")
                    .background(
                        if (index % 2 == 0) {
                            Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingAccentAltGlowPrimary))
                        } else {
                            Color.Transparent
                        },
                        RoundedCornerShape(16.dp),
                    )
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Vertical { size, space -> (space - size) / 2 },
            ) {
                Image(painterResource(row.iconRes), null, Modifier.size(24.dp).testTag("comparison-row-icon-$index"))
                DaxText(
                    text = AnnotatedString(stringResource(row.textRes), paragraphStyle = OnboardingBodyParagraphStyle),
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    style = DuckDuckGoTheme.typography.onboardingBodyInContext,
                    color = Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingTextPrimary)),
                )
                Image(painterResource(CommonR.drawable.ic_cross_recolorable_gray60_24), null, Modifier.size(40.dp).padding(8.dp))
                Box(
                    Modifier.padding(
                        start = 3.dp,
                        end = 4.dp,
                    ).width(
                        1.dp,
                    ).fillMaxHeight().testTag(
                        "comparison-divider-$index",
                    ).background(Color(LocalContext.current.getColorFromAttr(CommonR.attr.daxColorContainer))),
                )
                ChartCheck(index, readyAnimated, skipped)
            }
        }
    }
}

@Composable
private fun ChartHeaderIcon(resource: Int, size: Float, background: Color) {
    val iconSize = with(LocalDensity.current) { size.dp.toPx().toInt().toDp() }
    Box(
        Modifier.size(40.dp).onboardingBottomShadow(8.dp).background(background, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment { child, space, _ -> IntOffset((space.width - child.width) / 2, (space.height - child.height) / 2) },
    ) {
        Image(painterResource(resource), null, Modifier.size(iconSize).testTag("comparison-header-icon-$resource"))
    }
}

@Composable
private fun ChartCheck(index: Int, readyAnimated: Boolean?, skipped: Boolean) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0f) }
    var drawTick by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    val snap = readyAnimated == false || skipped
    LaunchedEffect(readyAnimated, skipped) {
        if (readyAnimated == null) return@LaunchedEffect
        if (snap) {
            alpha.snapTo(1f)
            scale.snapTo(1f)
        } else {
            delay(index * 130L)
            launch { alpha.animateTo(1f, tween(130, easing = Easing { AccelerateDecelerateInterpolator().getInterpolation(it) })) }
            launch {
                scale.animateTo(1f, tween(400, easing = Easing { OvershootInterpolator(2.4f).getInterpolation(it) }))
                settled = true
            }
            delay(180)
            drawTick = true
        }
    }
    Image(
        painter = if (snap || settled) {
            painterResource(CommonR.drawable.check_recolorable_24)
        } else {
            rememberAnimatedVectorPainter(
                AnimatedImageVector.animatedVectorResource(CommonR.drawable.avd_check_recolorable_24),
                drawTick,
            )
        },
        contentDescription = null,
        modifier = Modifier.size(40.dp).padding(8.dp).testTag("comparison-check-$index").graphicsLayer {
            this.alpha = if (snap) 1f else alpha.value
            scaleX = if (snap) 1f else scale.value
            scaleY = scaleX
        },
    )
}

@Composable
internal fun Modifier.onboardingBottomShadow(radius: Dp): Modifier {
    if (Build.VERSION.SDK_INT < 28) return this
    val outline = remember(radius) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
                Outline.Rounded(
                    RoundRect(
                        -3.dp.toPx(),
                        3.dp.toPx(),
                        size.width + 3.dp.toPx(),
                        size.height + 3.dp.toPx(),
                        CornerRadius(radius.toPx()),
                    ),
                )
            }
        }
    }
    return shadow(12.dp, outline, clip = false, ambientColor = colorResource(CommonR.color.background_omnibar_shadow), spotColor = Color.Transparent)
}
