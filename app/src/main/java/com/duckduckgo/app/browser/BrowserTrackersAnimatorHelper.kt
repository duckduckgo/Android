/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.TrackerLogo.ImageLogo
import com.duckduckgo.app.browser.TrackerLogo.LetterLogo
import com.duckduckgo.app.browser.TrackerLogo.StackedLogo
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.DaxDialogCta
import com.duckduckgo.app.privacy.renderer.TrackersRenderer
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.mobile.android.ui.store.AppTheme
import com.duckduckgo.mobile.android.ui.view.getColorFromAttr
import com.duckduckgo.mobile.android.ui.view.toPx
import timber.log.Timber
import java.util.*

interface TrackersAnimatorListener {
    fun onAnimationFinished()
}

class BrowserLottieTrackersAnimatorHelper {
    private var listener: TrackersAnimatorListener? = null
    private lateinit var trackersAnimation: LottieAnimationView
    private lateinit var shieldAnimation: LottieAnimationView
    private var currentCta: Cta? = null
    private val isCtaVisible
        get() = currentCta is DaxDialogCta.DaxTrackersBlockedCta

    private var partialAnimation: Boolean = false

    fun startTrackersAnimation(
        cta: Cta?,
        activity: Activity,
        shieldAnimationView: LottieAnimationView,
        trackersAnimationView: LottieAnimationView,
        omnibarViews: List<View>,
        entities: List<Entity>?,
        theme: AppTheme
    ) {
        this.trackersAnimation = trackersAnimationView
        this.shieldAnimation = shieldAnimationView
        this.currentCta = cta

        Timber.i("Lottie: isAnimating ${trackersAnimationView.isAnimating}")
        if (trackersAnimationView.isAnimating) return

        if (entities.isNullOrEmpty()) { // no badge nor tracker animations
            Timber.i("Lottie: entities.isNullOrEmpty()")
            return
        }

        entities.forEach {
            Timber.i("Lottie: entities ${it.name}")
        }
        val logos = getLogos(activity, entities)
        if (logos.isEmpty()) {
            Timber.i("Lottie: logos empty")
            return
        }

        val animationRawRes = getAnimationRawRes(logos, theme)
        with(trackersAnimationView) {
            this.setCacheComposition(false) // ensure assets are not cached
            this.setAnimation(animationRawRes)
            this.maintainOriginalImageBounds = true
            this.setImageAssetDelegate { asset ->
                Timber.i("Lottie: ${asset?.id} ${asset?.fileName}")
                when (asset?.id) {
                    "image_0" -> {
                        kotlin.runCatching { logos[0].asDrawable(activity) }
                            .getOrDefault(
                                ContextCompat.getDrawable(activity, R.drawable.network_logo_blank)!!.toBitmap()
                            )
                    }
                    "image_1" -> {
                        kotlin.runCatching { logos[1].asDrawable(activity) }
                            .getOrDefault(
                                ContextCompat.getDrawable(activity, R.drawable.network_logo_blank)!!.toBitmap()
                            )
                    }
                    "image_2" -> {
                        kotlin.runCatching { logos[2].asDrawable(activity) }
                            .getOrDefault(
                                ContextCompat.getDrawable(activity, R.drawable.network_logo_blank)!!.toBitmap()
                            )
                    }
                    "image_3" ->
                        kotlin.runCatching { logos[3].asDrawable(activity) }
                            .getOrNull()
                    else -> TODO()
                }
            }
            this.removeAllAnimatorListeners()
            this.addAnimatorListener(object : AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {
                    Timber.i("Lottie: onAnimationStart")
                    if (partialAnimation) return
                    animateOmnibarOut(omnibarViews).start()
                }

                override fun onAnimationEnd(animation: Animator?) {
                    Timber.i("Lottie: onAnimationEnd")
                    if (!isCtaVisible) {
                        animateOmnibarIn(omnibarViews).start()
                        partialAnimation = false
                        listener?.onAnimationFinished()
                    }
                }

                override fun onAnimationCancel(animation: Animator?) {
                }

                override fun onAnimationRepeat(animation: Animator?) {
                }
            })

            Timber.i("Lottie: isCtaVisible $isCtaVisible")
            if (isCtaVisible) {
                this.setMaxProgress(0.5f)
                shieldAnimationView.setMaxProgress(0.5f)
            } else {
                this.setMaxProgress(1f)
                shieldAnimationView.setMaxProgress(1f)
            }
            shieldAnimationView.playAnimation()
            this.playAnimation()
        }
    }

    private fun getAnimationRawRes(
        logos: List<TrackerLogo>,
        theme: AppTheme
    ): Int {
        val trackers = logos.size
        return when {
            trackers == 1 -> if (theme.isLightModeEnabled()) R.raw.light_trackers_1 else R.raw.dark_trackers_1
            trackers == 2 -> if (theme.isLightModeEnabled()) R.raw.light_trackers_2 else R.raw.dark_trackers_2
            trackers >= 3 -> if (theme.isLightModeEnabled()) R.raw.light_trackers else R.raw.dark_trackers
            else -> TODO()
        }
    }

    private fun TrackerLogo.asDrawable(activity: Activity): Bitmap {
        return kotlin.runCatching {
            when (this) {
                is ImageLogo -> ContextCompat.getDrawable(activity, resId)!!.toBitmap()
                is LetterLogo -> generateDefaultDrawable(activity, this.trackerLetter).toBitmap(24.toPx(), 24.toPx())
                is StackedLogo -> ContextCompat.getDrawable(activity, R.drawable.network_logo_more)!!.toBitmap()
            }
        }.getOrThrow()
    }

    private fun getLogos(
        activity: Activity,
        entities: List<Entity>
    ): List<TrackerLogo> {
        if (activity.packageName == null) return emptyList()
        val trackerLogoList = entities
            .asSequence()
            .distinct()
            .take(MAX_LOGOS_SHOWN + 1)
            .sortedWithDisplayNamesStartingWithVowelsToTheEnd()
            .map {
                val resId = TrackersRenderer().networkLogoIcon(activity, it.name)
                if (resId == null) {
                    LetterLogo(it.displayName.take(1))
                } else {
                    ImageLogo(resId)
                }
            }.toMutableList()

        return if (trackerLogoList.size <= MAX_LOGOS_SHOWN) {
            trackerLogoList
        } else {
            trackerLogoList.take(MAX_LOGOS_SHOWN)
                .toMutableList()
                .apply { add(StackedLogo()) }
        }
    }

    fun generateDefaultDrawable(
        context: Context,
        letter: String
    ): Drawable {
        Timber.i("Lottie: will create logo for $letter")
        return object : Drawable() {

            private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.toolbarIconColor)
            }

            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.omnibarRoundedFieldBackgroundColor)
                typeface = Typeface.SANS_SERIF
            }

            override fun draw(canvas: Canvas) {
                val centerX = bounds.width() * 0.5f
                val centerY = bounds.height() * 0.5f
                textPaint.textSize = (bounds.width() / 2).toFloat()
                val textWidth: Float = textPaint.measureText(letter) * 0.5f
                val textBaseLineHeight = textPaint.fontMetrics.ascent * -0.4f
                canvas.drawCircle(centerX, centerY, centerX, backgroundPaint)
                canvas.drawText(letter, centerX - textWidth, centerY + textBaseLineHeight, textPaint)
                Timber.i("Lottie: will create logo for $letter")
            }

            override fun setAlpha(alpha: Int) {
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
            }

            override fun getOpacity(): Int {
                return PixelFormat.TRANSPARENT
            }
        }
    }

    fun removeListener() {
        listener = null
    }

    fun setListener(animatorListener: TrackersAnimatorListener) {
        listener = animatorListener
    }

    fun cancelAnimations(
        omnibarViews: List<View>,
        container: ViewGroup
    ) {
        Timber.i("Lottie: cancelAnimations")
        stopTrackersAnimation()
        omnibarViews.forEach { it.alpha = 1f }
        container.alpha = 0f
    }

    fun finishPartialTrackerAnimation() {
        currentCta = null
        partialAnimation = true
        Timber.i("Lottie: finishTrackerAnimation")
        this.trackersAnimation.setMinAndMaxProgress(0.5f, 1f)
        this.shieldAnimation.setMinAndMaxProgress(0.5f, 1f)
        this.trackersAnimation.playAnimation()
        this.shieldAnimation.playAnimation()
    }

    private fun stopTrackersAnimation() {
        Timber.i("Lottie: stopTrackersAnimation")
        if (!::trackersAnimation.isInitialized || !::shieldAnimation.isInitialized) return

        Timber.i("Lottie: stopTrackersAnimation real")
        if (trackersAnimation.isAnimating) {
            trackersAnimation.cancelAnimation()
            trackersAnimation.progress = 1f
        }
        if (shieldAnimation.isAnimating) {
            shieldAnimation.cancelAnimation()
            shieldAnimation.progress = 0f
        }
    }

    private fun animateOmnibarOut(views: List<View>): AnimatorSet {
        Timber.i("Lottie: animateOmnibarOut")
        val animators = views.map {
            animateFadeOut(it)
        }
        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    private fun animateOmnibarIn(views: List<View>): AnimatorSet {
        Timber.i("Lottie: animateOmnibarIn")
        val animators = views.map {
            animateFadeIn(it)
        }
        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    private fun animateFadeOut(
        view: View,
        durationInMs: Long = DEFAULT_ANIMATION_DURATION
    ): ObjectAnimator {
        Timber.i("Lottie: animateFadeOut")
        return ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            duration = durationInMs
        }
    }

    private fun animateFadeIn(view: View): ObjectAnimator {
        Timber.i("Lottie: animateFadeIn")
        if (view.alpha == 1f) {
            return ObjectAnimator.ofFloat(view, "alpha", 1f, 1f).apply {
                duration = DEFAULT_ANIMATION_DURATION
            }
        }

        return ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    private fun Sequence<Entity>.sortedWithDisplayNamesStartingWithVowelsToTheEnd(): Sequence<Entity> {
        return sortedWith(compareBy { "AEIOU".contains(it.displayName.take(1)) })
    }

    companion object {
        private const val DEFAULT_ANIMATION_DURATION = 150L
        private const val MAX_LOGOS_SHOWN = 3
    }
}

sealed class TrackerLogo(val resId: Int) {
    class ImageLogo(resId: Int) : TrackerLogo(resId)
    class LetterLogo(
        val trackerLetter: String = "",
        resId: Int = R.drawable.other_tracker_bg
    ) : TrackerLogo(resId)

    class StackedLogo(resId: Int = R.drawable.other_tracker_bg) : TrackerLogo(resId)
}
