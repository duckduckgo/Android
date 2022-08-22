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
import android.animation.PropertyValuesHolder
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.animation.addListener
import androidx.core.animation.doOnEnd
import androidx.core.view.children
import androidx.core.widget.TextViewCompat
import androidx.transition.Scene
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.Transition.TransitionListener
import androidx.transition.TransitionManager
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.DaxDialogCta
import com.duckduckgo.app.privacy.renderer.TrackersRenderer
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.mobile.android.ui.store.AppTheme
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.view.toPx
import com.duckduckgo.mobile.android.R as CommonR

interface TrackersAnimatorListener {
    fun onAnimationFinished()
}

class BrowserTrackersAnimatorHelper(
    private val omnibarViews: List<View>,
    private val privacyGradeView: View,
    private val cookieView: LottieAnimationView,
    private val cookieScene: ViewGroup,
    private val dummyCookieView: View,
    private val container: ConstraintLayout,
    private val appTheme: AppTheme,
) {
    private var trackersAnimation: AnimatorSet = AnimatorSet()
    private var pulseAnimation: AnimatorSet = AnimatorSet()
    private var listener: TrackersAnimatorListener? = null
    private var enqueueCookiesAnimation = false
    private var hasCookiesAnimationBeenCanceled = false
    private var isCookiesAnimationRunning = false

    lateinit var firstScene: Scene
    lateinit var secondScene: Scene

    fun createCookiesAnimation(context: Context) {
        if (!trackersAnimation.isRunning) {
            startCookiesAnimation(context)
        } else {
            enqueueCookiesAnimation = true
        }
    }

    private fun tryToStartCookiesAnimation(context: Context) {
        if (enqueueCookiesAnimation) {
            startCookiesAnimation(context)
            enqueueCookiesAnimation = false
        }
    }

    private fun startCookiesAnimation(context: Context) {
        isCookiesAnimationRunning = true
        firstScene = Scene.getSceneForLayout(cookieScene, R.layout.cookie_scene_1, context)
        secondScene = Scene.getSceneForLayout(cookieScene, R.layout.cookie_scene_2, context)

        hasCookiesAnimationBeenCanceled = false
        val allOmnibarViews: List<View> = (omnibarViews + privacyGradeView).toList()
        cookieView.show()
        cookieView.alpha = 1F
        if (appTheme.isLightModeEnabled()) {
            cookieView.setAnimation(R.raw.cookie_icon_animated_light)
        } else {
            cookieView.setAnimation(R.raw.cookie_icon_animated_dark)
        }
        cookieView.progress = 0F

        val slideInCookiesTransition: Transition = createSlideTransition()
        val slideOutCookiesTransition: Transition = createSlideTransition()

        // After the slide in transitions, wait 1s and then begin slide out + fade out animation views
        slideInCookiesTransition.addListener(object : TransitionListener {
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
                                        .with(animateFadeOut(dummyCookieView, COOKIES_ANIMATION_FADE_OUT_DURATION))
                                    addListener(
                                        doOnEnd {
                                            cookieView.gone()
                                            isCookiesAnimationRunning = false
                                        }
                                    )
                                    start()
                                }
                            } else {
                                isCookiesAnimationRunning = false
                            }
                        }
                    )
                    start()
                }
            }
            override fun onTransitionStart(transition: Transition) {}
            override fun onTransitionCancel(transition: Transition) {}
            override fun onTransitionPause(transition: Transition) {}
            override fun onTransitionResume(transition: Transition) {}
        })

        // After slide out finished, hide view and fade in omnibar views
        slideOutCookiesTransition.addListener(object : TransitionListener {
            override fun onTransitionEnd(transition: Transition) {
                if (!hasCookiesAnimationBeenCanceled) {
                    AnimatorSet().apply {
                        play(animateOmnibarIn(allOmnibarViews))
                        start()
                    }
                    cookieScene.gone()
                } else {
                    isCookiesAnimationRunning = false
                }
            }
            override fun onTransitionStart(transition: Transition) {}
            override fun onTransitionCancel(transition: Transition) {}
            override fun onTransitionPause(transition: Transition) {}
            override fun onTransitionResume(transition: Transition) {}
        })

        // When lottie animation begins, begin the transition to slide in the text
        cookieView.addAnimatorListener(object : AnimatorListener {
            override fun onAnimationStart(p0: Animator?) {
                TransitionManager.go(secondScene, slideInCookiesTransition)
            }
            override fun onAnimationEnd(p0: Animator?) {}
            override fun onAnimationCancel(p0: Animator?) {}
            override fun onAnimationRepeat(p0: Animator?) {}
        })

        // Here the animations begins. Fade out omnibar, fade in dummy view and after that start lottie animation
        AnimatorSet().apply {
            play(animateOmnibarOut(allOmnibarViews)).with(animateFadeIn(dummyCookieView))
            addListener(onEnd = {
                cookieScene.show()
                cookieScene.alpha = 1F
                cookieView.playAnimation()
            })
            start()
        }
    }

    private fun createSlideTransition(): Transition {
        val slideInCookiesTransition: Transition = Slide(Gravity.START)
        slideInCookiesTransition.duration = COOKIES_ANIMATION_DURATION
        return slideInCookiesTransition
    }

    fun startTrackersAnimation(
        context: Context,
        cta: Cta?,
        entities: List<Entity>?,
    ) {
        if (entities.isNullOrEmpty()) {
            listener?.onAnimationFinished()
            tryToStartCookiesAnimation(context)
            return
        }

        if (isCookiesAnimationRunning) return // If cookies animation is running let it finish to avoid weird glitches with the other animations

        val logoViews: List<View> = getLogosViewListInContainer(context, container, entities)
        if (logoViews.isEmpty()) {
            listener?.onAnimationFinished()
            tryToStartCookiesAnimation(context)
            return
        }

        if (!trackersAnimation.isRunning) {
            trackersAnimation = if (cta is DaxDialogCta.DaxTrackersBlockedCta) {
                createPartialTrackersAnimation(container, omnibarViews, logoViews).apply {
                    start()
                }
            } else {
                createFullTrackersAnimation(context, container, omnibarViews, logoViews).apply {
                    start()
                }
            }
        }
    }

    fun startPulseAnimation() {
        if (!pulseAnimation.isRunning) {
            val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
                privacyGradeView,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 0.9f, 1f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 0.9f, 1f)
            )
            scaleDown.repeatCount = ObjectAnimator.INFINITE
            scaleDown.duration = PULSE_ANIMATION_DURATION

            pulseAnimation = AnimatorSet().apply {
                play(scaleDown)
                start()
            }
        }
    }

    fun stopPulseAnimation() {
        if (pulseAnimation.isRunning) {
            pulseAnimation.end()
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
        stopTrackersAnimation()
        stopPulseAnimation()
        stopCookiesAnimation()
        listener?.onAnimationFinished()
        omnibarViews.forEach { it.alpha = 1f }
        container.alpha = 0f
    }

    private fun stopCookiesAnimation() {
        hasCookiesAnimationBeenCanceled = true
        if (this::firstScene.isInitialized) {
            TransitionManager.go(firstScene)
        }
        privacyGradeView.alpha = 1f
        dummyCookieView.alpha = 0f
        cookieScene.gone()
        cookieView.gone()
    }

    fun finishTrackerAnimation(context: Context) {
        trackersAnimation = AnimatorSet().apply {
            play(animateLogosSlideOut(container.children.toList()))
                .before(animateOmnibarIn(omnibarViews))
                .before(animateFadeOut(container))
            start()
            addListener(onEnd = {
                listener?.onAnimationFinished()
                tryToStartCookiesAnimation(context)
            })
        }
    }

    private fun getLogosViewListInContainer(
        context: Context,
        container: ViewGroup,
        entities: List<Entity>
    ): List<View> {
        container.removeAllViews()
        container.alpha = 0f
        val logos = createTrackerLogoList(context, entities)
        return createLogosViewList(context, container, logos)
    }

    private fun createLogosViewList(
        context: Context,
        container: ViewGroup,
        resourcesId: List<TrackerLogo>
    ): List<View> {
        return resourcesId.map {
            val frameLayout = when (it) {
                is TrackerLogo.ImageLogo -> createTrackerImageLogo(context, it)
                is TrackerLogo.LetterLogo -> createTrackerTextLogo(context, it)
                is TrackerLogo.StackedLogo -> createTrackerStackedLogo(context, it)
            }
            container.addView(frameLayout)
            return@map frameLayout
        }
    }

    private fun getParams(): FrameLayout.LayoutParams {
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.CENTER
        return params
    }

    private fun createAnimatedDrawable(context: Context): AnimatedVectorDrawableCompat? {
        return AnimatedVectorDrawableCompat.create(context, R.drawable.network_cross_anim)
    }

    private fun createFrameLayoutContainer(context: Context): FrameLayout {
        val frameLayout = FrameLayout(context)
        frameLayout.alpha = 0f
        frameLayout.id = View.generateViewId()
        frameLayout.layoutParams = getParams()
        frameLayout.setBackgroundResource(R.drawable.background_tracker_logo)
        return frameLayout
    }

    private fun createImageView(
        context: Context,
        resId: Int
    ): ImageView {
        val imageView = ImageView(context)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setBackgroundResource(resId)
        imageView.id = View.generateViewId()
        imageView.layoutParams = getParams()
        return imageView
    }

    private fun createTextView(context: Context): AppCompatTextView {
        val textView = AppCompatTextView(context)
        textView.gravity = Gravity.CENTER
        TextViewCompat.setTextAppearance(textView, CommonR.style.UnknownTrackerText)
        textView.layoutParams = getParams()

        return textView
    }

    private fun createTrackerTextLogo(
        context: Context,
        trackerLogo: TrackerLogo.LetterLogo
    ): FrameLayout {
        val animatedDrawable = createAnimatedDrawable(context)

        val animationView = ImageView(context)
        animationView.setImageDrawable(animatedDrawable)
        animationView.layoutParams = getParams()

        val textView = createTextView(context)
        textView.setBackgroundResource(trackerLogo.resId)
        textView.text = trackerLogo.trackerLetter

        val frameLayout = createFrameLayoutContainer(context)
        frameLayout.addView(textView)
        frameLayout.addView(animationView)

        return frameLayout
    }

    private fun createTrackerStackedLogo(
        context: Context,
        trackerLogo: TrackerLogo.StackedLogo
    ): FrameLayout {
        val imageView = createImageView(context, trackerLogo.resId)
        val frameLayout = createFrameLayoutContainer(context)

        frameLayout.addView(imageView)

        return frameLayout
    }

    private fun createTrackerImageLogo(
        context: Context,
        trackerLogo: TrackerLogo.ImageLogo
    ): FrameLayout {
        val imageView = createImageView(context, trackerLogo.resId)
        val animatedDrawable = createAnimatedDrawable(context)
        imageView.setImageDrawable(animatedDrawable)

        val frameLayout = createFrameLayoutContainer(context)
        frameLayout.addView(imageView)

        return frameLayout
    }

    private fun createTrackerLogoList(
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
                    TrackerLogo.LetterLogo(it.displayName.take(1))
                } else {
                    TrackerLogo.ImageLogo(resId)
                }
            }
            .toMutableList()

        return if (trackerLogoList.size <= MAX_LOGOS_SHOWN) {
            trackerLogoList
        } else {
            trackerLogoList.take(MAX_LOGOS_SHOWN)
                .toMutableList()
                .apply { add(TrackerLogo.StackedLogo()) }
        }
    }

    private fun animateLogosBlocked(views: List<View>) {
        views.map {
            if (it is FrameLayout) {
                val view: ImageView? = it.children.filter { child -> child is ImageView }.firstOrNull() as ImageView?
                view?.let {
                    val animatedVectorDrawableCompat = view.drawable as? AnimatedVectorDrawableCompat
                    animatedVectorDrawableCompat?.start()
                }
            }
        }
    }

    private fun createFullTrackersAnimation(
        context: Context,
        container: ConstraintLayout,
        omnibarViews: List<View>,
        logoViews: List<View>
    ): AnimatorSet {
        val finalAnimation = AnimatorSet().apply {
            play(animateLogosSlideOut(logoViews))
                .after(TRACKER_LOGOS_DELAY_ON_SCREEN)
                .before(animateFadeOut(container))
                .before(animateOmnibarIn(omnibarViews))
        }

        return AnimatorSet().apply {
            playSequentially(
                createPartialTrackersAnimation(container, omnibarViews, logoViews),
                finalAnimation
            )
            start()
            addListener(onEnd = {
                listener?.onAnimationFinished()
                tryToStartCookiesAnimation(context)
            })
        }
    }

    private fun createPartialTrackersAnimation(
        container: ConstraintLayout,
        omnibarViews: List<View>,
        logoViews: List<View>
    ): AnimatorSet {
        applyConstraintSet(container, logoViews)
        container.alpha = 1f
        animateLogosBlocked(logoViews)

        return AnimatorSet().apply {
            play(animateLogosSlideIn(logoViews)).after(animateOmnibarOut(omnibarViews))
        }
    }

    private fun applyConstraintSet(
        container: ConstraintLayout,
        views: List<View>
    ) {
        val constraints = ConstraintSet()
        constraints.clone(container)

        views.mapIndexed { index, view ->
            constraints.connect(view.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            constraints.connect(view.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            if (index == 0) {
                constraints.connect(view.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, FIRST_LOGO_MARGIN_IN_DP.toPx())
            } else {
                constraints.setTranslationX(view.id, (NORMAL_LOGO_MARGIN_IN_DP.toPx() * index))
                constraints.connect(view.id, ConstraintSet.START, views[index - 1].id, ConstraintSet.END, 0)
            }
            if (index == views.size - 1) {
                if (views.size == MAX_LOGOS_SHOWN + 1) {
                    constraints.setTranslationX(view.id, (STACKED_LOGO_MARGIN_IN_DP.toPx() * index))
                }
                constraints.connect(view.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            }
        }

        views.reversed().map { it.bringToFront() }

        val viewIds = views.map { it.id }.toIntArray()

        if (viewIds.size > 1) {
            constraints.createHorizontalChain(
                ConstraintSet.PARENT_ID,
                ConstraintSet.LEFT,
                ConstraintSet.PARENT_ID,
                ConstraintSet.RIGHT,
                viewIds,
                null,
                ConstraintSet.CHAIN_SPREAD
            )
        }

        constraints.applyTo(container)
    }

    private fun calculateMarginInPx(position: Int): Int = START_MARGIN_IN_DP.toPx() + (position * LOGO_SIZE_IN_DP.toPx())

    private fun stopTrackersAnimation() {
        if (trackersAnimation.isRunning) {
            trackersAnimation.end()
        }
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

    private fun animateLogosSlideIn(views: List<View>): AnimatorSet {
        val initialMargin = (views.first().layoutParams as ConstraintLayout.LayoutParams).marginStart.toFloat()
        val slideInAnimators = views.mapIndexed { index, it ->
            val margin = calculateMarginInPx(index) + initialMargin
            animateSlideIn(it, margin)
        }
        val fadeInAnimators = views.map {
            animateFadeIn(it)
        }
        return AnimatorSet().apply {
            playTogether(slideInAnimators + fadeInAnimators)
        }
    }

    private fun animateLogosSlideOut(views: List<View>): AnimatorSet {
        val slideOutAnimators = views.map {
            animateSlideOut(it)
        }
        val fadeOutAnimators = views.map {
            animateFadeOut(it)
        }
        return AnimatorSet().apply {
            playTogether(slideOutAnimators + fadeOutAnimators)
        }
    }

    private fun animateSlideIn(
        view: View,
        margin: Float
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "x", 0f, margin + view.translationX).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    private fun animateSlideOut(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "x", 0f).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    private fun animateFadeOut(
        view: View,
        durationInMs: Long = DEFAULT_ANIMATION_DURATION
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            duration = durationInMs
        }
    }

    private fun animateFadeIn(
        view: View,
        durationInMs: Long = DEFAULT_ANIMATION_DURATION
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = durationInMs
        }
    }

    private fun Sequence<Entity>.sortedWithDisplayNamesStartingWithVowelsToTheEnd(): Sequence<Entity> {
        return sortedWith(compareBy { "AEIOU".contains(it.displayName.take(1)) })
    }

    companion object {
        private const val TRACKER_LOGOS_DELAY_ON_SCREEN = 2400L
        private const val DEFAULT_ANIMATION_DURATION = 150L
        private const val PULSE_ANIMATION_DURATION = 1500L
        private const val MAX_LOGOS_SHOWN = 3
        private const val LOGO_SIZE_IN_DP = 26
        private const val START_MARGIN_IN_DP = 10
        private const val STACKED_LOGO_MARGIN_IN_DP = -11.5f
        private const val NORMAL_LOGO_MARGIN_IN_DP = -7f
        private const val FIRST_LOGO_MARGIN_IN_DP = 29
        private const val COOKIES_ANIMATION_DELAY = 1000L
        private const val COOKIES_ANIMATION_DURATION = 300L
        private const val COOKIES_ANIMATION_FADE_OUT_DURATION = 800L
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
