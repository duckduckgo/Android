/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.inputscreen.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi
import com.duckduckgo.common.ui.view.toPx

@RequiresApi(33)
class BottomBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val renderNode = RenderNode(null)
    private var targetView: View? = null
    private var maxBlurRadiusPx = MAX_BLUR_RADIUS_DP.toPx().toFloat()
    private val selfLocationOnScreen = IntArray(2)
    private val targetLocationOnScreen = IntArray(2)

    private val blurShader = RuntimeShader(
        """
        uniform shader inputShader;
        uniform vec2 size;
        uniform float maxBlurRadius;

        const float TAU = 6.28318530718;
        const int DIRECTIONS = 16;
        const int QUALITY = 3;
        const float SAMPLES = float(DIRECTIONS * QUALITY + 1);

        vec2 mirrorCoord(vec2 coord) {
            if (coord.x < 0.0) coord.x = -coord.x;
            else if (coord.x > size.x) coord.x = 2.0 * size.x - coord.x;

            if (coord.y < 0.0) coord.y = -coord.y;
            else if (coord.y > size.y) coord.y = 2.0 * size.y - coord.y;

            return coord;
        }

        vec4 main(vec2 fragCoord) {
            vec2 uv = fragCoord.xy / size.xy;
            float radius = maxBlurRadius * uv.y;

            vec4 pixel = inputShader.eval(mirrorCoord(fragCoord));

            for (int i = 0; i < DIRECTIONS; i++) {
                float angle = (TAU * float(i)) / float(DIRECTIONS);
                vec2 dir = vec2(cos(angle), sin(angle));

                for (int j = 1; j <= QUALITY; j++) {
                    float frac = float(j) / float(QUALITY);
                    vec2 offset = dir * radius * frac;
                    vec2 sampleCoord = mirrorCoord(fragCoord + offset);
                    pixel += inputShader.eval(sampleCoord);
                }
            }
            return pixel / SAMPLES;
        }
        """.trimIndent(),
    )

    private val shaderEffect: RenderEffect
        get() = RenderEffect.createRuntimeShaderEffect(blurShader, "inputShader")

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        background = null
    }

    fun setTargetView(view: View) {
        targetView = view
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val viewToBlur = targetView ?: return

        val w = width
        val h = height
        if (w == 0 || h == 0) return

        getLocationOnScreen(selfLocationOnScreen)
        viewToBlur.getLocationOnScreen(targetLocationOnScreen)
        val dx = (selfLocationOnScreen[0] - targetLocationOnScreen[0]).toFloat()
        val dy = (selfLocationOnScreen[1] - targetLocationOnScreen[1]).toFloat()

        renderNode.setPosition(0, 0, w, h)
        renderNode.beginRecording().apply {
            translate(-dx, -dy)
            viewToBlur.draw(this)
            renderNode.endRecording()
        }

        blurShader.setFloatUniform("size", w.toFloat(), h.toFloat())
        blurShader.setFloatUniform("maxBlurRadius", maxBlurRadiusPx)

        renderNode.setRenderEffect(shaderEffect)
        canvas.drawRenderNode(renderNode)
    }

    companion object {
        const val MAX_BLUR_RADIUS_DP = 6
    }
}
