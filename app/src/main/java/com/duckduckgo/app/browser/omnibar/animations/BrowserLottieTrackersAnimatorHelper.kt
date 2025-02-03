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

package com.duckduckgo.app.browser.omnibar.animations

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.transition.Scene
import android.transition.Slide
import android.transition.Transition
import android.transition.Transition.TransitionListener
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import androidx.core.animation.addListener
import androidx.core.animation.doOnEnd
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo.ImageLogo
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo.LetterLogo
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo.StackedLogo
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(FragmentScope::class)
class BrowserLottieTrackersAnimatorHelper @Inject constructor(
    private val theme: AppTheme,
) : BrowserTrackersAnimatorHelper {

    private var listener: TrackersAnimatorListener? = null
    private var trackersAnimation: LottieAnimationView? = null
    private var shieldAnimation: LottieAnimationView? = null

    private var trackersBlockedAnimationView: DaxTextView? = null
    private var trackersBlockedCountAnimationView: DaxTextView? = null

    private lateinit var cookieView: LottieAnimationView
    private lateinit var cookieScene: ViewGroup
    private lateinit var cookieViewBackground: View
    private var cookieCosmeticHide: Boolean = false

    private var enqueueCookiesAnimation = false
    private var isCookiesAnimationRunning = false
    private var hasCookiesAnimationBeenCanceled = false

    lateinit var firstScene: Scene
    lateinit var secondScene: Scene

    override fun startTrackersAnimation(
        context: Context,
        shieldAnimationView: LottieAnimationView,
        trackersAnimationView: LottieAnimationView,
        omnibarViews: List<View>,
        entities: List<Entity>?,
    ) {
        if (isCookiesAnimationRunning) return // If cookies animation is running let it finish to avoid weird glitches with the other animations
        if (trackersAnimationView.isAnimating) return

        this.trackersAnimation = trackersAnimationView
        this.shieldAnimation = shieldAnimationView

        if (entities.isNullOrEmpty()) { // no badge nor tracker animations
            tryToStartCookiesAnimation(context, omnibarViews)
            return
        }

        val logos = getLogos(context, entities)
        if (logos.isEmpty()) {
            tryToStartCookiesAnimation(context, omnibarViews)
            return
        }

        val animationRawRes = getAnimationRawRes(logos, theme)
        with(trackersAnimationView) {
            this.setCacheComposition(false) // ensure assets are not cached
            this.setAnimation(animationRawRes)
            this.maintainOriginalImageBounds = true
            this.setImageAssetDelegate(TrackersLottieAssetDelegate(context, logos))
            this.removeAllAnimatorListeners()
            this.addAnimatorListener(
                object : AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {
                        animateOmnibarOut(omnibarViews).start()
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        animateOmnibarIn(omnibarViews).start()
                        tryToStartCookiesAnimation(context, omnibarViews)
                        listener?.onAnimationFinished(emptyList())
                    }

                    override fun onAnimationCancel(animation: Animator) {
                    }

                    override fun onAnimationRepeat(animation: Animator) {
                    }
                },
            )

            this.setMaxProgress(1f)
            shieldAnimationView.setMaxProgress(1f)
            shieldAnimationView.playAnimation()
            this.playAnimation()
        }
    }

    override fun startExperimentTrackersAnimation(
        context: Context,
        shieldAnimationView: LottieAnimationView,
        trackersBlockedAnimationView: DaxTextView,
        trackersBlockedCountAnimationView: DaxTextView,
        omnibarViews: List<View>,
        entities: List<Entity>?,
    ) {
        this.trackersBlockedAnimationView = trackersBlockedAnimationView
        this.trackersBlockedCountAnimationView = trackersBlockedCountAnimationView

        if (entities.isNullOrEmpty()) {
            tryToStartCookiesAnimation(context, omnibarViews)
            return
        }

        val logos = getLogos(context, entities)
        if (logos.isEmpty()) {
            tryToStartCookiesAnimation(context, omnibarViews)
            return
        }

        animateTrackersBlockedView(omnibarViews)
        animateTrackersBlockedCountView(context, entities, logos, omnibarViews)
    }

    private fun animateTrackersBlockedView(omnibarViews: List<View>) {
        val fadeInAnimation = AlphaAnimation(0f, 1f).apply {
            duration = 500L
            startOffset = 100L
        }
        val slideInAnimation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF,
            -0.08f,
            Animation.RELATIVE_TO_SELF,
            0f,
            Animation.RELATIVE_TO_SELF,
            0f,
            Animation.RELATIVE_TO_SELF,
            0f,
        ).apply {
            duration = 500L
            startOffset = 200L
        }

        slideInAnimation.interpolator = LinearOutSlowInInterpolator()

        val animationSet = AnimationSet(false).apply {
            addAnimation(fadeInAnimation)
            addAnimation(slideInAnimation)
        }

        trackersBlockedAnimationView?.show()
        trackersBlockedAnimationView?.startAnimation(animationSet)

        animationSet.setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    animateOmnibarOut(omnibarViews).start()
                }

                override fun onAnimationEnd(animation: Animation?) {}

                override fun onAnimationRepeat(animation: Animation?) {}
            },
        )
    }

    private fun animateTrackersBlockedCountView(context: Context, entities: List<Entity>, logos: List<TrackerLogo>, omnibarViews: List<View>) {
        val fadeInAnimation = AlphaAnimation(0f, 1f).apply {
            duration = 200L
        }

        trackersBlockedCountAnimationView?.show()
        trackersBlockedCountAnimationView?.startAnimation(fadeInAnimation)

        fadeInAnimation.setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    updateTrackersCountWithAnimation(context, entities, logos, omnibarViews)
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            },
        )
    }

    private fun updateTrackersCountWithAnimation(
        context: Context,
        entities: List<Entity>,
        logos: List<TrackerLogo>,
        omnibarViews: List<View>,
    ) {
        val handler = Handler(Looper.getMainLooper())
        val trackerCountUpdateDelay = if (entities.size >= TRACKER_THRESHOLD) {
            HIGH_TRACKERS_COUNT_ANIMATION_DURATION
        } else {
            LOW_TRACKERS_COUNT_ANIMATION_DURATION
        }
        val animationCompletionDelay = 1000L

        fun updateTackersCountText(index: Int) {
            if (index <= entities.size) {
                trackersBlockedCountAnimationView?.text = index.toString()
                handler.postDelayed({ updateTackersCountText(index + 1) }, trackerCountUpdateDelay)
            } else {
                handler.postDelayed(
                    {
                        trackersBlockedAnimationView?.gone()
                        trackersBlockedCountAnimationView?.text = ""
                        trackersBlockedCountAnimationView?.gone()
                        animateOmnibarIn(omnibarViews).start()
                        listener?.onAnimationFinished(logos)
                    },
                    animationCompletionDelay,
                )

                handler.postDelayed(
                    {
                        tryToStartCookiesAnimation(context, omnibarViews)
                    },
                    2500L,
                )
            }
        }

        updateTackersCountText(1)
    }

    override fun createCookiesAnimation(
        context: Context,
        omnibarViews: List<View>,
        cookieBackground: View,
        cookieAnimationView: LottieAnimationView,
        cookieScene: ViewGroup,
        cookieCosmeticHide: Boolean,
        enqueueCookieAnimation: Boolean,
    ) {
        this.cookieScene = cookieScene
        this.cookieViewBackground = cookieBackground
        this.cookieView = cookieAnimationView
        this.cookieCosmeticHide = cookieCosmeticHide

        if (enqueueCookieAnimation) {
            this.enqueueCookiesAnimation = true
        } else if (this.trackersAnimation?.isAnimating != true) {
            startCookiesAnimation(context, omnibarViews)
        } else {
            enqueueCookiesAnimation = false
        }
    }

    override fun removeListener() {
        listener = null
    }

    override fun setListener(animatorListener: TrackersAnimatorListener) {
        listener = animatorListener
    }

    override fun cancelAnimations(
        omnibarViews: List<View>,
    ) {
        stopTrackersAnimation()
        stopCookiesAnimation()
        omnibarViews.forEach { it.alpha = 1f }
    }

    private fun tryToStartCookiesAnimation(
        context: Context,
        omnibarViews: List<View>,
    ) {
        if (enqueueCookiesAnimation) {
            startCookiesAnimation(context, omnibarViews)
            enqueueCookiesAnimation = false
        }
    }

    private fun startCookiesAnimation(
        context: Context,
        omnibarViews: List<View>,
    ) {
        isCookiesAnimationRunning = true

        if (cookieCosmeticHide) {
            firstScene = Scene.getSceneForLayout(cookieScene, R.layout.cookie_cosmetic_scene_1, context)
            secondScene = Scene.getSceneForLayout(cookieScene, R.layout.cookie_cosmetic_scene_2, context)
        } else {
            firstScene = Scene.getSceneForLayout(cookieScene, R.layout.cookie_scene_1, context)
            secondScene = Scene.getSceneForLayout(cookieScene, R.layout.cookie_scene_2, context)
        }

        hasCookiesAnimationBeenCanceled = false
        val allOmnibarViews: List<View> = (omnibarViews).filterNotNull().toList()
        cookieView.show()
        cookieView.alpha = 0F
        if (theme.isLightModeEnabled()) {
            cookieView.setAnimation(R.raw.cookie_icon_animated_light)
        } else {
            cookieView.setAnimation(R.raw.cookie_icon_animated_dark)
        }
        cookieView.progress = 0F

        val slideInCookiesTransition: Transition = createSlideTransition()
        val slideOutCookiesTransition: Transition = createSlideTransition()

        // After the slide in transitions, wait 1s and then begin slide out + fade out animation views
        slideInCookiesTransition.addListener(
            object : TransitionListener {
                override fun onTransitionEnd(transition: Transition) {
                    AnimatorSet().apply {
                        play(animateFadeIn(cookieView, 0L)) // Fake animation because the delay doesn't really work
                        startDelay = COOKIES_ANIMATION_DELAY
                        addListener(
                            doOnEnd {
                                if (!hasCookiesAnimationBeenCanceled) {
                                    AnimatorSet().apply {
                                        TransitionManager.go(firstScene, slideOutCookiesTransition)
                                        play(animateFadeOut(cookieView, COOKIES_ANIMATION_FADE_OUT_DURATION))
                                            .with(animateFadeOut(cookieViewBackground, COOKIES_ANIMATION_FADE_OUT_DURATION))
                                        addListener(
                                            doOnEnd {
                                                cookieView.gone()
                                                isCookiesAnimationRunning = false
                                                listener?.onAnimationFinished(emptyList())
                                            },
                                        )
                                        start()
                                    }
                                } else {
                                    isCookiesAnimationRunning = false
                                    listener?.onAnimationFinished(emptyList())
                                }
                            },
                        )
                        start()
                    }
                }

                override fun onTransitionStart(transition: Transition) {}
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            },
        )

        // After slide out finished, hide view and fade in omnibar views
        slideOutCookiesTransition.addListener(
            object : TransitionListener {
                override fun onTransitionEnd(transition: Transition) {
                    if (!hasCookiesAnimationBeenCanceled) {
                        AnimatorSet().apply {
                            play(animateOmnibarIn(allOmnibarViews))
                            start()
                        }
                        cookieScene.gone()
                    } else {
                        isCookiesAnimationRunning = false
                        listener?.onAnimationFinished(emptyList())
                    }
                }

                override fun onTransitionStart(transition: Transition) {}
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            },
        )

        // When lottie animation begins, begin the transition to slide in the text
        cookieView.addAnimatorListener(
            object : AnimatorListener {
                override fun onAnimationStart(p0: Animator) {
                    TransitionManager.go(secondScene, slideInCookiesTransition)
                }

                override fun onAnimationEnd(p0: Animator) {}
                override fun onAnimationCancel(p0: Animator) {}
                override fun onAnimationRepeat(p0: Animator) {}
            },
        )

        // Here the animations begins. Fade out omnibar, fade in dummy view and after that start lottie animation
        AnimatorSet().apply {
            play(animateOmnibarOut(allOmnibarViews)).with(animateFadeIn(cookieViewBackground)).with(animateFadeIn(cookieView))
            addListener(
                onEnd = {
                    cookieScene.show()
                    cookieScene.alpha = 1F
                    cookieView.playAnimation()
                },
            )
            start()
        }
    }

    private fun createSlideTransition(): Transition {
        val slideInCookiesTransition: Transition = Slide(Gravity.START)
        slideInCookiesTransition.duration = COOKIES_ANIMATION_DURATION
        return slideInCookiesTransition
    }

    private fun getAnimationRawRes(
        logos: List<TrackerLogo>,
        theme: AppTheme,
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
        entities: List<Entity>,
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
        val trackersAnimation = this.trackersAnimation ?: return
        val shieldAnimation = this.shieldAnimation ?: return

        if (trackersAnimation.isAnimating) {
            trackersAnimation.cancelAnimation()
            trackersAnimation.progress = 1f
        }
        if (shieldAnimation.isAnimating) {
            shieldAnimation.cancelAnimation()
            shieldAnimation.progress = 0f
        }
    }

    private fun stopCookiesAnimation() {
        if (!::cookieViewBackground.isInitialized || !::cookieView.isInitialized) return

        hasCookiesAnimationBeenCanceled = true
        if (this::firstScene.isInitialized) {
            TransitionManager.go(firstScene)
        }
        shieldAnimation?.alpha = 1f
        cookieViewBackground.alpha = 0f
        cookieScene.gone()
        cookieView.gone()
    }

    private fun animateOmnibarOut(views: List<View>): AnimatorSet {
        val animators = views.map {
            animateFadeOut(it)
        }
        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    private fun animateOmnibarIn(views: List<View>): AnimatorSet {
        val animators = views.map {
            animateFadeIn(it)
        }
        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    private fun animateFadeOut(
        view: View,
        durationInMs: Long = DEFAULT_ANIMATION_DURATION,
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            duration = durationInMs
        }
    }

    private fun animateFadeIn(
        view: View,
        durationInMs: Long = DEFAULT_ANIMATION_DURATION,
    ): ObjectAnimator {
        if (view.alpha == 1f) {
            return ObjectAnimator.ofFloat(view, "alpha", 1f, 1f).apply {
                duration = durationInMs
            }
        }

        return ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = durationInMs
        }
    }

    private fun Sequence<Entity>.sortedWithDisplayNamesStartingWithVowelsToTheEnd(): Sequence<Entity> {
        return sortedWith(compareBy { "AEIOU".contains(it.displayName.take(1)) })
    }

    companion object {
        private const val DEFAULT_ANIMATION_DURATION = 150L
        private const val MAX_LOGOS_SHOWN = 3
        private const val COOKIES_ANIMATION_DELAY = 1000L
        private const val COOKIES_ANIMATION_DURATION = 300L
        private const val COOKIES_ANIMATION_FADE_OUT_DURATION = 800L
        private const val TRACKER_THRESHOLD = 10
        private const val HIGH_TRACKERS_COUNT_ANIMATION_DURATION = 100L
        private const val LOW_TRACKERS_COUNT_ANIMATION_DURATION = 150L
    }
}

sealed class TrackerLogo() {
    class ImageLogo(val resId: Int) : TrackerLogo()
    class LetterLogo(
        val trackerLetter: String = "",
    ) : TrackerLogo()

    class StackedLogo(val resId: Int = R.drawable.network_logo_more) : TrackerLogo()
}
