/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.dashboard.impl.animations

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.mobile.android.ui.store.AppTheme
import com.duckduckgo.privacy.dashboard.api.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.privacy.dashboard.api.animations.TrackersAnimatorListener
import com.duckduckgo.privacy.dashboard.impl.R
import com.duckduckgo.privacy.dashboard.impl.animations.TrackerLogo.ImageLogo
import com.duckduckgo.privacy.dashboard.impl.animations.TrackerLogo.LetterLogo
import com.duckduckgo.privacy.dashboard.impl.animations.TrackerLogo.StackedLogo
import com.duckduckgo.trackerdetection.model.Entity
import timber.log.Timber

class BrowserLottieTrackersAnimatorHelper(
    val context: Context,
    val theme: AppTheme
) : BrowserTrackersAnimatorHelper {

    private var listener: TrackersAnimatorListener? = null
    private lateinit var trackersAnimation: LottieAnimationView
    private lateinit var shieldAnimation: LottieAnimationView

    private var runPartialAnimation: Boolean = false
    private var completePartialAnimation: Boolean = false

    override fun startTrackersAnimation(
        runPartialAnimation: Boolean,
        shieldAnimationView: LottieAnimationView,
        trackersAnimationView: LottieAnimationView,
        omnibarViews: List<View>,
        entities: List<Entity>?
    ) {
        this.runPartialAnimation = runPartialAnimation
        this.trackersAnimation = trackersAnimationView
        this.shieldAnimation = shieldAnimationView

        Timber.i("Lottie: isAnimating ${trackersAnimationView.isAnimating}")
        if (trackersAnimationView.isAnimating) return

        if (entities.isNullOrEmpty()) { // no badge nor tracker animations
            Timber.i("Lottie: entities.isNullOrEmpty()")
            return
        }

        entities.forEach {
            Timber.i("Lottie: entities ${it.name}")
        }
        val logos = getLogos(context, entities)
        if (logos.isEmpty()) {
            Timber.i("Lottie: logos empty")
            return
        }

        val animationRawRes = getAnimationRawRes(logos, theme)
        with(trackersAnimationView) {
            this.setCacheComposition(false) // ensure assets are not cached
            this.setAnimation(animationRawRes)
            this.maintainOriginalImageBounds = true
            this.setImageAssetDelegate(TrackersLottieAssetDelegate(context, logos))
            this.removeAllAnimatorListeners()
            this.addAnimatorListener(object : AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {
                    Timber.i("Lottie: onAnimationStart")
                    if (completePartialAnimation) return
                    animateOmnibarOut(omnibarViews).start()
                }

                override fun onAnimationEnd(animation: Animator?) {
                    Timber.i("Lottie: onAnimationEnd")
                    if (!runPartialAnimation) {
                        animateOmnibarIn(omnibarViews).start()
                        completePartialAnimation = false
                        listener?.onAnimationFinished()
                    }
                }

                override fun onAnimationCancel(animation: Animator?) {
                }

                override fun onAnimationRepeat(animation: Animator?) {
                }
            })

            Timber.i("Lottie: ctaVisible? $runPartialAnimation")
            if (runPartialAnimation) {
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

    override fun removeListener() {
        listener = null
    }

    override fun setListener(animatorListener: TrackersAnimatorListener) {
        listener = animatorListener
    }

    override fun cancelAnimations(
        omnibarViews: List<View>
    ) {
        Timber.i("Lottie: cancelAnimations")
        stopTrackersAnimation()
        omnibarViews.forEach { it.alpha = 1f }
    }

    override fun finishPartialTrackerAnimation() {
        runPartialAnimation = false
        completePartialAnimation = true
        Timber.i("Lottie: finishTrackerAnimation")
        this.trackersAnimation.setMinAndMaxProgress(0.5f, 1f)
        this.shieldAnimation.setMinAndMaxProgress(0.5f, 1f)
        this.trackersAnimation.playAnimation()
        this.shieldAnimation.playAnimation()
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

    private fun getLogos(
        context: Context,
        entities: List<Entity>
    ): List<TrackerLogo> {
        if (context.packageName == null) return emptyList()
        val trackerLogoList = entities
            .asSequence()
            .distinct()
            .take(MAX_LOGOS_SHOWN + 1)
            .sortedWithDisplayNamesStartingWithVowelsToTheEnd()
            .map {
                val resId = TrackersRenderer().networkLogoIcon(context, it.name)
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

internal sealed class TrackerLogo() {
    class ImageLogo(val resId: Int) : TrackerLogo()
    class LetterLogo(
        val trackerLetter: String = ""
    ) : TrackerLogo()

    class StackedLogo(val resId: Int = R.drawable.network_logo_more) : TrackerLogo()
}
