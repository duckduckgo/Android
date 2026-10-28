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
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.duckduckgo.common.ui.view.getColorFromAttr
import kotlin.math.roundToInt
import androidx.appcompat.R as AppCompatR
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.R as MaterialR

@Composable
internal fun OnboardingRadioDrawable(selected: Boolean, color: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var initialized by remember(context, configuration) { mutableStateOf(false) }
    key(context, configuration, selected) {
        var atEnd by remember { mutableStateOf(!initialized) }
        val image = AnimatedImageVector.animatedVectorResource(
            if (selected) AppCompatR.drawable.btn_radio_off_to_on_mtrl_animation else AppCompatR.drawable.btn_radio_on_to_off_mtrl_animation,
        )
        LaunchedEffect(Unit) {
            initialized = true
            atEnd = true
        }
        Image(
            painter = rememberAnimatedVectorPainter(image, atEnd),
            contentDescription = null,
            modifier = modifier.size(32.dp),
            colorFilter = ColorFilter.tint(Color(color)),
        )
    }
}

@Composable
internal fun OnboardingSwitchDrawable(checked: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val track = remember(context, configuration) {
        checkNotNull(AppCompatResources.getDrawable(context, MaterialR.drawable.mtrl_switch_track)).mutate()
    }
    val thumb = remember(context, configuration) {
        checkNotNull(AppCompatResources.getDrawable(context, MaterialR.drawable.mtrl_switch_thumb_checked)).mutate()
    }
    val interpolator = remember { AccelerateDecelerateInterpolator() }
    val progress by animateFloatAsState(
        if (checked) 1f else 0f,
        tween(250, easing = Easing(interpolator::getInterpolation)),
        label = "onboarding switch",
    )
    val trackOff = context.getColorFromAttr(CommonR.attr.daxColorSwitchTrackOff)
    val trackOn = context.getColorFromAttr(CommonR.attr.daxColorSwitchTrackOn)
    val thumbOff = context.getColorFromAttr(CommonR.attr.daxColorSwitchThumbOff)
    val thumbOn = context.getColorFromAttr(CommonR.attr.daxColorSwitchThumbOn)
    Canvas(modifier.size(52.dp, 48.dp)) {
        val top = (size.height.roundToInt() - track.intrinsicHeight) / 2
        track.setTint(ColorUtils.blendARGB(trackOff, trackOn, progress))
        track.setBounds(0, top, size.width.roundToInt(), top + track.intrinsicHeight)
        track.draw(drawContext.canvas.nativeCanvas)
        val position = if (layoutDirection == LayoutDirection.Rtl) 1f - progress else progress
        val left = (position * (size.width.roundToInt() - thumb.intrinsicWidth)).roundToInt()
        thumb.setTint(ColorUtils.blendARGB(thumbOff, thumbOn, progress))
        thumb.setBounds(left, top, left + thumb.intrinsicWidth, top + thumb.intrinsicHeight)
        thumb.draw(drawContext.canvas.nativeCanvas)
    }
}
