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

package com.duckduckgo.subscriptions.impl.onboarding.confetti

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class ConfettiShape {
    STAR,
    BLOB,
    RECT,
    STRIP,
}

internal enum class ConfettiColorFamily {
    MANDARIN,
    PONDWATER,
    LILYPAD,
    BLOSSOM,
    POLLEN,
}

internal data class ConfettiConfiguration(
    val particleCount: Int = 60,
    val startVelocity: Double = 33.0,
    val spread: Double = 96.0,
    val decay: Double = 0.92,
    val gravity: Double = 1.4,
    val drift: Double = 0.0,
    val duration: Double = 5.0,
    val fadeOutVariance: Double = 0.6,
    val xSpin: Double = 0.0,
    val ySpin: Double = 0.0,
    val zSpin: Double = 0.2,
    val size: Double = 3.0,
    val sizeVariation: Double = 1.6,
    val enabledShapes: Set<ConfettiShape> = setOf(ConfettiShape.STAR, ConfettiShape.BLOB, ConfettiShape.RECT),
    val enabledColorFamilies: Set<ConfettiColorFamily> = setOf(
        ConfettiColorFamily.MANDARIN,
        ConfettiColorFamily.BLOSSOM,
        ConfettiColorFamily.POLLEN,
    ),
)

internal class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var configuration: ConfettiConfiguration = ConfettiConfiguration()
        set(value) {
            field = value
            clearActiveBurst()
            invalidate()
        }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val camera = Camera()
    private val cameraMatrix = Matrix()
    private val random = kotlin.random.Random.Default

    private val pxPerDp = resources.displayMetrics.density

    private val activeParticles = mutableListOf<ActiveParticle>()
    private val particleBitmapCache = mutableMapOf<String, Bitmap>()

    private var burstProgress = 0f

    private var burstAnimator: ValueAnimator? = null
    private var cleanupRunnable: Runnable? = null

    override fun onDetachedFromWindow() {
        clearActiveBurst()
        super.onDetachedFromWindow()
    }

    fun fire() {
        val activeShapes = weightedShapes.filter { configuration.enabledShapes.contains(it) }
        if (activeShapes.isEmpty()) return

        val activeShades = configuration.enabledColorFamilies
            .flatMap { colorFamilies[it].orEmpty() }
        if (activeShades.isEmpty()) return

        clearActiveBurst()

        val ticks = (configuration.duration * 60.0).roundToInt()
        repeat(configuration.particleCount) {
            val spreadRadians = configuration.spread * (PI / 180.0)
            val angle = -PI / 2 + (0.5 * spreadRadians - random.nextDouble() * spreadRadians)
            val velocity = configuration.startVelocity * 0.5 + random.nextDouble() * configuration.startVelocity
            val wobbleSpeed = min(0.11, random.nextDouble() * 0.1 + 0.05)
            val wobbleOffset = random.nextDouble() * 10.0
            val pieceSize = 6 * configuration.size + random.nextDouble() * (6 * configuration.size * configuration.sizeVariation)
            val sizeDepth = pieceSize / max(6 * configuration.size, 1.0)
            val depthGravity = configuration.gravity * (1 + (sizeDepth - 1) * 0.35)
            val tiltRotations = 2 + random.nextDouble() * 4
            val rotation = random.nextDouble() * 360
            val fadeOutEnd = 1 - random.nextDouble() * configuration.fadeOutVariance

            val shape = activeShapes.random(random)
            val shade = activeShades.random(random)
            val variant = shapeVariants[shape]?.random(random) ?: return@repeat
            val bitmap = particleBitmap(shape, variant, shade)

            val keyframes = ConfettiPhysics.computeKeyframes(
                ConfettiPhysics.Input(
                    angle = angle,
                    startVelocity = velocity,
                    decay = configuration.decay,
                    gravity = depthGravity,
                    drift = configuration.drift,
                    wobbleSpeed = wobbleSpeed,
                    wobbleOffset = wobbleOffset,
                    size = configuration.size,
                    ticks = ticks,
                    xTiltRotations = tiltRotations * configuration.xSpin,
                    tiltRotations = tiltRotations * configuration.ySpin,
                    zTiltRotations = tiltRotations * configuration.zSpin,
                    rotation = rotation,
                    fadeOutEnd = fadeOutEnd,
                ),
            )

            activeParticles += ActiveParticle(
                bitmap = bitmap,
                keyframes = keyframes,
                sizePx = pieceSize.toFloat() * pxPerDp,
            )
        }

        if (activeParticles.isEmpty()) return

        burstProgress = 0f

        burstAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (configuration.duration * 1000).roundToInt().toLong().coerceAtLeast(1L)
            interpolator = null
            addUpdateListener {
                burstProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        cleanupRunnable = Runnable {
            activeParticles.clear()
            burstAnimator = null
            cleanupRunnable = null
            invalidate()
        }.also {
            val cleanupDelay = ((configuration.duration + 0.5) * 1000).roundToInt().toLong()
            postDelayed(it, cleanupDelay)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (activeParticles.isEmpty()) return
        drawParticles(canvas, burstProgress, width / 2f, height / 2f)
    }

    private fun clearActiveBurst() {
        burstAnimator?.cancel()
        burstAnimator = null
        cleanupRunnable?.let(::removeCallbacks)
        cleanupRunnable = null

        burstProgress = 0f
        activeParticles.clear()
    }

    private fun drawParticles(
        canvas: Canvas,
        progress: Float,
        originX: Float,
        originY: Float,
    ) {
        val clampedProgress = progress.coerceIn(0f, 1f)

        activeParticles.forEach { particle ->
            val state = sampleKeyframes(particle.keyframes, clampedProgress)
            val alpha = (state.opacity * 255f).roundToInt().coerceIn(0, 255)
            if (alpha <= 0) return@forEach

            val bitmap = particle.bitmap
            val halfW = bitmap.width / 2f
            val halfH = bitmap.height / 2f
            val baseScale = particle.sizePx / RASTER_PX.toFloat()
            val totalScale = baseScale * state.scale

            particlePaint.alpha = alpha
            canvas.withSave {
                translate(originX + state.tx * pxPerDp, originY + state.ty * pxPerDp)

                if (state.rotX != 0f || state.rotY != 0f) {
                    camera.save()
                    camera.rotateX(state.rotX)
                    camera.rotateY(state.rotY)
                    camera.getMatrix(cameraMatrix)
                    camera.restore()
                    concat(cameraMatrix)
                }

                rotate(state.rotZ)
                scale(totalScale, totalScale)
                drawBitmap(bitmap, -halfW, -halfH, particlePaint)
            }
        }
    }

    private fun particleBitmap(
        shape: ConfettiShape,
        variant: ConfettiShapeVariant,
        shade: ConfettiShade,
    ): Bitmap {
        val cacheKey = "${shape.name}|${variant.fillPath}|${variant.strokePath}|${shade.fill}|${shade.stroke}"
        particleBitmapCache[cacheKey]?.let { return it }

        val fillPath = PathParser.createPathFromPathData(variant.fillPath)
        val strokePath = PathParser.createPathFromPathData(variant.strokePath)

        val bitmap = createBitmap(RASTER_PX, RASTER_PX)
        val canvas = Canvas(bitmap)
        val scale = RASTER_PX / 40f
        canvas.scale(scale, scale)

        fillPaint.color = shade.fill
        strokePaint.color = shade.stroke

        canvas.drawPath(fillPath, fillPaint)
        if (variant.clipStrokeToFill) {
            canvas.withClip(fillPath) {
                drawPath(strokePath, strokePaint)
            }
        } else {
            canvas.drawPath(strokePath, strokePaint)
        }

        particleBitmapCache[cacheKey] = bitmap
        return bitmap
    }

    private fun sampleKeyframes(output: ConfettiPhysics.Output, progress: Float): RenderState {
        val position = progress * ConfettiPhysics.KEYFRAME_STEPS
        val lower = position.toInt().coerceIn(0, ConfettiPhysics.KEYFRAME_STEPS)
        val upper = min(lower + 1, ConfettiPhysics.KEYFRAME_STEPS)
        val t = (position - lower).coerceIn(0f, 1f)

        fun lerp(values: DoubleArray): Float {
            val a = values[lower]
            val b = values[upper]
            return (a + (b - a) * t).toFloat()
        }

        return RenderState(
            tx = lerp(output.tx),
            ty = lerp(output.ty),
            scale = lerp(output.scale),
            rotX = lerp(output.rotX),
            rotY = lerp(output.rotY),
            rotZ = lerp(output.rotZ),
            opacity = lerp(output.opacity),
        )
    }
}

internal object ConfettiPhysics {
    const val KEYFRAME_STEPS: Int = 40
    private const val SCALE_DURATION_FRACTION: Double = 0.08

    data class Input(
        val angle: Double,
        val startVelocity: Double,
        val decay: Double,
        val gravity: Double,
        val drift: Double,
        val wobbleSpeed: Double,
        val wobbleOffset: Double,
        val size: Double,
        val ticks: Int,
        val xTiltRotations: Double,
        val tiltRotations: Double,
        val zTiltRotations: Double,
        val rotation: Double,
        val fadeOutEnd: Double,
    )

    class Output(
        val tx: DoubleArray,
        val ty: DoubleArray,
        val scale: DoubleArray,
        val rotX: DoubleArray,
        val rotY: DoubleArray,
        val rotZ: DoubleArray,
        val opacity: DoubleArray,
    )

    fun computeKeyframes(input: Input): Output {
        val fadeOutStart = max(0.0, input.fadeOutEnd - 0.5)
        val fadeOutMid = fadeOutStart + (input.fadeOutEnd - fadeOutStart) * 0.6

        val tx = DoubleArray(KEYFRAME_STEPS + 1)
        val ty = DoubleArray(KEYFRAME_STEPS + 1)
        val scale = DoubleArray(KEYFRAME_STEPS + 1)
        val rotX = DoubleArray(KEYFRAME_STEPS + 1)
        val rotY = DoubleArray(KEYFRAME_STEPS + 1)
        val rotZ = DoubleArray(KEYFRAME_STEPS + 1)
        val opacity = DoubleArray(KEYFRAME_STEPS + 1)

        var velocity = input.startVelocity
        var x = 0.0
        var y = 0.0
        var wobble = input.wobbleOffset
        var tick = 0

        for (step in 0..KEYFRAME_STEPS) {
            val t = step.toDouble() / KEYFRAME_STEPS.toDouble()
            if (step > 0) {
                val targetTick = ((step.toDouble() * input.ticks.toDouble()) / KEYFRAME_STEPS.toDouble()).roundToInt()
                while (tick < targetTick) {
                    x += cos(input.angle) * velocity + input.drift
                    y += sin(input.angle) * velocity + input.gravity * 3
                    velocity *= input.decay
                    wobble += input.wobbleSpeed
                    tick += 1
                }
            }

            tx[step] = if (step == 0) 0.0 else x + cos(wobble) * 15 * input.size
            ty[step] = y
            scale[step] = computeScale(t)
            rotX[step] = input.xTiltRotations * 360 * t
            rotY[step] = input.tiltRotations * 360 * t
            rotZ[step] = input.rotation + input.zTiltRotations * 360 * t
            opacity[step] = computeOpacity(
                progress = t,
                fadeOutStart = fadeOutStart,
                fadeOutMid = fadeOutMid,
                fadeOutEnd = input.fadeOutEnd,
            )
        }

        return Output(
            tx = tx,
            ty = ty,
            scale = scale,
            rotX = rotX,
            rotY = rotY,
            rotZ = rotZ,
            opacity = opacity,
        )
    }

    private fun computeScale(progress: Double): Double {
        val earlyGrow = SCALE_DURATION_FRACTION * 0.6
        if (progress < earlyGrow) return (progress / earlyGrow) * 1.15
        if (progress < SCALE_DURATION_FRACTION) {
            val st = (progress - earlyGrow) / (SCALE_DURATION_FRACTION * 0.4)
            return 1.15 - st * 0.15
        }
        return 1.0
    }

    private fun computeOpacity(
        progress: Double,
        fadeOutStart: Double,
        fadeOutMid: Double,
        fadeOutEnd: Double,
    ): Double {
        if (progress <= fadeOutStart) return 1.0
        if (progress <= fadeOutMid) {
            return 1 - ((progress - fadeOutStart) / (fadeOutMid - fadeOutStart)) * 0.5
        }
        if (progress <= fadeOutEnd) {
            return 0.5 - ((progress - fadeOutMid) / (fadeOutEnd - fadeOutMid)) * 0.5
        }
        return 0.0
    }
}

private data class ConfettiShade(
    val fill: Int,
    val stroke: Int,
)

private data class ConfettiShapeVariant(
    val fillPath: String,
    val strokePath: String,
    val clipStrokeToFill: Boolean,
)

private class ActiveParticle(
    val bitmap: Bitmap,
    val keyframes: ConfettiPhysics.Output,
    val sizePx: Float,
)

private data class RenderState(
    val tx: Float,
    val ty: Float,
    val scale: Float,
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float,
    val opacity: Float,
)

private const val RASTER_PX: Int = 96

private val weightedShapes: List<ConfettiShape> = listOf(
    ConfettiShape.STAR,
    ConfettiShape.BLOB,
    ConfettiShape.RECT,
    ConfettiShape.RECT,
    ConfettiShape.STRIP,
    ConfettiShape.STRIP,
)

private val colorFamilies: Map<ConfettiColorFamily, List<ConfettiShade>> = mapOf(
    ConfettiColorFamily.MANDARIN to listOf(
        ConfettiShade(fill = colorHex(0xF05F2B), stroke = colorHex(0x9E2B08)),
        ConfettiShade(fill = colorHex(0xFF8D5C), stroke = colorHex(0xCC3B0A)),
        ConfettiShade(fill = colorHex(0xFFB294), stroke = colorHex(0xF05F2B)),
    ),
    ConfettiColorFamily.PONDWATER to listOf(
        ConfettiShade(fill = colorHex(0x4397E0), stroke = colorHex(0x045EB2)),
        ConfettiShade(fill = colorHex(0x75B6EB), stroke = colorHex(0x1074CC)),
        ConfettiShade(fill = colorHex(0xA1D0F7), stroke = colorHex(0x4397E0)),
    ),
    ConfettiColorFamily.LILYPAD to listOf(
        ConfettiShade(fill = colorHex(0x589D88), stroke = colorHex(0x11604D)),
        ConfettiShade(fill = colorHex(0x84BBA8), stroke = colorHex(0x247A64)),
        ConfettiShade(fill = colorHex(0xAED5C2), stroke = colorHex(0x589D88)),
    ),
    ConfettiColorFamily.BLOSSOM to listOf(
        ConfettiShade(fill = colorHex(0x9F6EB8), stroke = colorHex(0x682A7A)),
        ConfettiShade(fill = colorHex(0xC19EDB), stroke = colorHex(0x7D4794)),
        ConfettiShade(fill = colorHex(0xD3B9EB), stroke = colorHex(0x9F6EB8)),
    ),
    ConfettiColorFamily.POLLEN to listOf(
        ConfettiShade(fill = colorHex(0xFAB341), stroke = colorHex(0xB66A1F)),
        ConfettiShade(fill = colorHex(0xFFC95C), stroke = colorHex(0xF5A031)),
        ConfettiShade(fill = colorHex(0xFFD885), stroke = colorHex(0xFAB341)),
    ),
)

@Suppress("ktlint:standard:max-line-length")
private val shapeVariants: Map<ConfettiShape, List<ConfettiShapeVariant>> = mapOf(
    ConfettiShape.STAR to listOf(
        ConfettiShapeVariant(
            fillPath = "M15.481 12.327L6.29029 9.26345C5.44511 8.98173 4.68121 9.8625 5.07964 10.6594L9.20436 18.9088C9.38362 19.2673 9.33148 19.6982 9.07187 20.0037L2.95594 27.1989C2.33223 27.9326 3.00436 29.0375 3.94273 28.8209L13.4457 26.6279C13.7893 26.5486 14.1494 26.6562 14.3932 26.9111L23.6611 36.6003C24.2225 37.1872 25.2124 36.8955 25.3658 36.0979L27.4048 25.4947C27.4647 25.1835 27.6684 24.9191 27.9541 24.782L37.9014 20.0073C38.6933 19.6272 38.6455 18.484 37.8247 18.1713L30.1178 15.2354C29.7432 15.0927 29.49 14.74 29.4746 14.3393L29.0766 3.99223C29.045 3.16996 28.0891 2.73568 27.449 3.25272L16.4255 12.1563C16.1604 12.3705 15.8044 12.4348 15.481 12.327Z",
            strokePath = "M6.40888 8.9077C6.32982 9.14487 6.25077 9.38204 6.17171 9.61921C8.10377 10.3072 10.0365 10.9931 11.9699 11.6771C13.0814 12.0703 14.1931 12.4629 15.305 12.8548C15.7962 13.0286 16.3747 12.9318 16.7858 12.6023C20.5101 9.69614 24.2275 6.78138 27.9379 3.85803C28.0686 3.74563 28.2939 3.87271 28.2731 4.02314C28.3665 7.47368 28.4658 10.924 28.571 14.3741C28.5885 15.1198 29.0904 15.8267 29.7927 16.0888C32.3542 17.0869 34.9171 18.0815 37.4813 19.0726C37.5094 19.0736 37.5025 19.1373 37.4795 19.1285C34.1573 20.7065 30.8379 22.2906 27.5215 23.8808C26.9577 24.1472 26.5371 24.6927 26.4229 25.3059C26.3995 25.4271 26.3762 25.5484 26.3529 25.6697C25.6965 29.0828 25.046 32.4971 24.4014 35.9125C24.4119 35.9249 24.3726 35.9439 24.3647 35.9272C21.262 32.7104 18.1521 29.5004 15.035 26.2972C14.5847 25.8235 13.8797 25.6172 13.248 25.7714C10.0869 26.5307 6.92696 27.2951 3.76821 28.0646C3.58243 28.1517 3.36149 27.8487 3.52331 27.6811C5.5308 25.2563 7.53455 22.8282 9.53456 20.3969C9.9427 19.9174 10.0205 19.1959 9.73047 18.6457C8.89411 17.0398 8.05631 15.4346 7.21704 13.8301C6.63111 12.7099 6.04446 11.5901 5.45711 10.4706C5.18979 10.0612 5.67373 9.43656 6.17171 9.61921C6.25077 9.38204 6.32982 9.14487 6.40888 8.9077C5.33202 8.45719 4.11998 9.78809 4.70217 10.8481C5.24534 11.9897 5.78921 13.1309 6.3338 14.2717C7.11383 15.9058 7.89531 17.5392 8.67825 19.1718C8.7462 19.3182 8.7148 19.4989 8.60918 19.6104C6.5319 21.9759 4.45837 24.3447 2.38857 26.7166C1.27033 27.8643 2.5466 30.0222 4.11726 29.5772C7.2938 28.8847 10.4692 28.1872 13.6433 27.4845C13.6847 27.4777 13.7273 27.4948 13.7514 27.525C16.8129 30.7813 19.8816 34.0307 22.9575 37.2733C23.96 38.4302 26.1073 37.8049 26.3301 36.2834C26.9983 32.8725 27.6605 29.4605 28.3169 26.0474C28.3402 25.9261 28.3635 25.8048 28.3868 25.6835C28.3867 25.6836 28.3868 25.6834 28.3867 25.6833C31.7018 24.0903 35.014 22.4913 38.3232 20.8862C39.8492 20.2438 39.7363 17.7712 38.168 17.2701C35.5943 16.3039 33.0193 15.3412 30.4429 14.382C30.4103 14.3734 30.3821 14.3418 30.3781 14.3046C30.218 10.8566 30.052 7.40885 29.8801 3.96133C29.883 2.54324 27.9952 1.71027 26.9601 2.64742C23.3216 5.65977 19.69 8.68071 16.0653 11.7103C15.9586 11.8051 15.7993 11.8417 15.6569 11.7992C14.5322 11.4456 13.4073 11.0926 12.2821 10.7403C10.325 10.1274 8.36729 9.51658 6.40888 8.9077Z",
            clipStrokeToFill = false,
        ),
    ),
    ConfettiShape.BLOB to listOf(
        ConfettiShapeVariant(
            fillPath = "M33.5 19.5C33.5 24.4706 26 32 18 32C12.7533 32 6 28.5 6 20.5C6 13.5 14.7533 8 20 8C25.2467 8 33.5 8 33.5 19.5Z",
            strokePath = "M34.25 19.5C33.75 19.5 33.25 19.5 32.75 19.5C32.6619 20.652 32.1366 21.8103 31.4656 22.8523C28.4507 27.3055 23.0506 30.4109 18 30.2759C12.5468 30.1223 7.72776 25.8929 7.99981 20.5C7.99994 20.3733 8.00376 20.2473 8.01117 20.1217C8.34733 15.0182 14.653 10.194 20 9.69955C25.3723 9.30169 30.8338 10.3118 32.1206 15.6921C32.4785 16.9001 32.6662 18.189 32.75 19.5C33.25 19.5 33.75 19.5 34.25 19.5C34.3319 18.1041 34.3003 16.6816 34.044 15.2438C33.6159 12.3442 31.6785 9.23011 28.7349 7.89424C25.8567 6.49858 22.7675 6.38636 20 6.30045C12.4298 6.81221 5.15151 11.4737 4.01813 19.8859C4.00612 20.0894 4.00006 20.2941 4.00019 20.5C3.54488 27.9886 11.0515 34.1631 18 33.7241C24.6643 33.4158 30.2486 29.296 33.1861 23.8221C33.8269 22.5197 34.3213 21.0656 34.25 19.5Z",
            clipStrokeToFill = true,
        ),
    ),
    ConfettiShape.RECT to listOf(
        ConfettiShapeVariant(
            fillPath = "M29.3537 15.2727C26.478 11.3702 21.6584 6.11242 20.1564 4.49434C19.8772 4.19362 19.4457 4.09943 19.0658 4.25461C17.6252 4.84314 14.0288 6.34528 11.6463 7.62338C9.37299 8.84295 6.34139 10.8583 4.87196 11.8563C4.39621 12.1795 4.29785 12.8336 4.65143 13.2871C6.16621 15.2302 9.98384 20.1692 12.4512 23.7273C14.8085 27.1265 17.9656 32.1959 19.1849 34.1707C19.4704 34.633 20.0705 34.783 20.5434 34.5154C22.1246 33.6205 25.5933 31.6974 28.1463 30.5714C30.5462 29.513 34.0237 28.3269 35.8229 27.733C36.4281 27.5332 36.7021 26.8322 36.3803 26.2821C35.0649 24.0336 31.9055 18.7358 29.3537 15.2727Z",
            strokePath = "M20.4312 4.23921C20.248 4.4093 20.0648 4.57938 19.8815 4.74947C20.8545 5.9208 21.8268 7.1043 22.79 8.28899C24.7954 10.7658 26.8032 13.2522 28.622 15.8119C30.2655 18.1613 31.7955 20.6375 33.3166 23.1112C34.0621 24.3283 34.798 25.5566 35.5192 26.7858C35.5183 26.7775 35.5245 26.7821 35.511 26.788C32.9063 27.6611 30.3185 28.558 27.7537 29.6811C25.0859 30.891 22.5819 32.2828 20.0848 33.7051C20.0471 33.7315 19.9918 33.721 19.969 33.6865C17.7586 30.1719 15.5257 26.6762 13.1259 23.2594C10.5824 19.6952 7.89577 16.2766 5.18136 12.874C5.0589 12.7363 5.08174 12.5032 5.23728 12.3942C7.4109 10.8839 9.61745 9.38402 11.8984 8.0932C13.2599 7.3392 14.6882 6.65807 16.1197 5.99731C17.1441 5.52632 18.1801 5.06687 19.2157 4.62157C19.4345 4.52154 19.7092 4.57067 19.8815 4.74947C20.0648 4.57938 20.248 4.4093 20.4312 4.23921C20.069 3.82573 19.4309 3.67582 18.9159 3.88765C17.8583 4.29823 16.8014 4.72371 15.7532 5.16321C14.2869 5.78083 12.8269 6.41833 11.3943 7.15355C8.99852 8.4124 6.74458 9.85274 4.50663 11.3185C3.72927 11.805 3.54088 12.9875 4.12151 13.7002C6.73469 17.1584 9.3425 20.6396 11.7765 24.1951C14.0719 27.6018 16.2477 31.1308 18.4009 34.6548C18.9092 35.5266 20.1399 35.8363 21.002 35.3258C23.4793 33.953 25.9968 32.5906 28.539 31.4618C30.9809 30.4134 33.5662 29.5289 36.1348 28.678C37.2905 28.3421 37.8963 26.8224 37.2413 25.7784C36.5157 24.5353 35.7752 23.2961 35.022 22.0664C33.4834 19.5652 31.8894 17.0976 30.0853 14.7336C28.0911 12.1622 25.916 9.80181 23.6873 7.49216C22.6183 6.38921 21.5308 5.302 20.4312 4.23921Z",
            clipStrokeToFill = false,
        ),
    ),
    ConfettiShape.STRIP to listOf(
        ConfettiShapeVariant(
            fillPath = "M34.9065 12.3992C33.763 10.6925 23.7074 14.7929 17.4679 18.3727C12.5136 21.2151 5.46093 25.1995 6.03269 27.4752C6.60445 29.7508 15.1095 24.0609 20.6125 21.2172C26.4413 18.2052 36.05 14.1059 34.9065 12.3992Z",
            strokePath = "M17.2812 18.0474C17.4056 18.2643 17.5301 18.4811 17.6545 18.6979C18.664 18.2151 19.6875 17.7533 20.7152 17.3066C24.8074 15.5895 29.08 13.7303 33.175 13.0341C33.4604 13.0018 33.7308 12.9896 33.9459 13.0097C34.1678 13.012 34.2536 13.1491 34.0765 12.9553C33.9934 12.857 33.9966 12.616 34.0135 12.6115C34.023 12.5916 33.9881 12.6698 33.9167 12.7563C33.7735 12.9353 33.549 13.1399 33.297 13.3406C32.7972 13.7374 32.2119 14.1155 31.6189 14.4753C30.4265 15.1945 29.1634 15.8619 27.8978 16.5141C25.3581 17.8206 22.7602 19.0582 20.1946 20.4085C17.7355 21.7175 15.3942 23.1554 13.0218 24.5064C11.8395 25.1792 10.6461 25.8439 9.44264 26.4166C8.84244 26.6998 8.22859 26.9664 7.64225 27.1425C7.34673 27.2306 7.05454 27.2914 6.84302 27.2913C6.73562 27.2928 6.67086 27.2701 6.67792 27.2749C6.68851 27.2771 6.72122 27.3428 6.70255 27.3069C6.55232 26.9788 7.1628 26.0697 7.79297 25.4673C8.44186 24.8193 9.18755 24.2241 9.95416 23.6534C11.4901 22.5172 13.1256 21.4762 14.7711 20.4563C15.7274 19.8648 16.6931 19.2814 17.6545 18.6979C17.5301 18.4811 17.4056 18.2643 17.2812 18.0474C16.293 18.5823 15.2981 19.1204 14.3099 19.6706C12.6082 20.6201 10.9133 21.5973 9.28034 22.7058C8.46321 23.2653 7.66067 23.8519 6.90709 24.5487C6.23694 25.2782 5.17436 25.9549 5.36283 27.6435C5.42818 27.9552 5.66638 28.3137 5.97604 28.4852C6.28379 28.6617 6.55636 28.6947 6.80459 28.7073C7.2808 28.7198 7.65383 28.6352 8.02999 28.5404C8.75753 28.3467 9.4209 28.0778 10.0697 27.7968C11.3618 27.2295 12.5952 26.5863 13.8123 25.9365C16.2464 24.6344 18.6267 23.2477 21.0304 22.0259C23.5707 20.7487 26.1907 19.5464 28.7785 18.2489C30.0713 17.5992 31.3658 16.9304 32.6394 16.1731C33.2761 15.7911 33.9111 15.3906 34.5349 14.901C34.8545 14.6446 35.1555 14.3926 35.4736 14.0045C35.6329 13.8017 35.7968 13.5754 35.9151 13.2212C36.0475 12.8714 36.0318 12.2482 35.7365 11.8431C35.177 11.0986 34.5543 11.0834 34.1349 11.0191C33.6914 10.9797 33.323 11.0064 32.9503 11.0467C28.1387 11.8819 24.1515 14.0048 20.1724 16.2364C19.1892 16.81 18.2215 17.4121 17.2812 18.0474Z",
            clipStrokeToFill = false,
        ),
    ),
)

private fun colorHex(rgb: Int): Int = Color.argb(255, (rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
