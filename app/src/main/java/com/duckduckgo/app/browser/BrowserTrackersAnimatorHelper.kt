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

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.widget.TextViewCompat
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.DaxDialogCta
import com.duckduckgo.app.global.view.toPx
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.renderer.TrackersRenderer
import com.duckduckgo.app.trackerdetection.model.Entity
import timber.log.Timber
import kotlin.math.log


interface TrackersAnimatorListener {
    fun onAnimationFinished()
}

class BrowserTrackersAnimatorHelper(val privacyGradeButton: ImageButton) {

    private var trackersAnimation: AnimatorSet = AnimatorSet()
    private var pulseAnimation: AnimatorSet = AnimatorSet()
    private var listener: TrackersAnimatorListener? = null

    fun startTrackersAnimation(cta: Cta?, activity: Activity, container: ViewGroup, omnibarViews: List<View>, entities: List<Entity>?, grade: PrivacyGrade?) {
        if (entities.isNullOrEmpty()) {
            listener?.onAnimationFinished()
            return
        }

        val logoViews: List<View> = getLogosViewListInContainer(activity, container, entities)
        if (logoViews.isEmpty()) {
            listener?.onAnimationFinished()
            return
        }

        val drawable = ContextCompat.getDrawable(activity, R.drawable.privacygrade_icon_loading)
        privacyGradeButton.setImageDrawable(drawable)

        if (!trackersAnimation.isRunning) {
            trackersAnimation = if (cta is DaxDialogCta.DaxTrackersBlockedCta) {
                createPartialTrackersAnimation(container, omnibarViews, logoViews).apply {
                    start()
                }
            } else {
                createFullTrackersAnimation(container, omnibarViews, logoViews, grade).apply {
                    start()
                }
            }
        }
    }

    fun removeListener() {
        listener = null
    }

    fun setListener(animatorListener: TrackersAnimatorListener) {
        listener = animatorListener
    }

    fun cancelAnimations() {
        if (trackersAnimation.isRunning) {
            trackersAnimation.end()
        }
        stopPulseAnimation()
    }

    fun areAnimationsRunning() = trackersAnimation.isRunning

    fun finishTrackerAnimation(fadeInViews: List<View>, container: ViewGroup) {
        val animateOmnibarIn = animateOmnibarIn(fadeInViews)
        val animateLogosOut = animateFadeOut(container)
        trackersAnimation = AnimatorSet().apply {
            play(animateLogosOut).before(animateOmnibarIn)
            start()
            addListener(
                onEnd = {
                    Timber.d("MARCOS DO ON END")
                    container.removeViews(1, container.children.count() - 1)
                    listener?.onAnimationFinished()
                }
            )
        }
    }

    private fun getLogosViewListInContainer(activity: Activity, container: ViewGroup, entities: List<Entity>): List<View> {
        container.removeAllViews()
        //container.alpha = 0f
        val logos = createResourcesIdList(activity, entities)
        val logosList = createLogosViewList(activity, container, logos)
//        logosList.reversed().map {
//            it.bringToFront()
//        }
        logosList.first().bringToFront()
        return logosList
    }

    private fun createLogosViewList(
        activity: Activity,
        container: ViewGroup,
        resourcesId: List<TrackerLogo>
    ): List<View> {
        return resourcesId.mapIndexed { index, it ->
            return@mapIndexed if (it.resId == R.drawable.other_tracker_bg) {
                val frameLayout = createTrackerTextLogo(activity, it, index)
                container.addView(frameLayout)
                frameLayout
            } else {
                val imageView = createTrackerImageLogo(activity, it, index)
                container.addView(imageView)
                imageView
            }
        }
    }

    private fun createTrackerTextLogo(activity: Activity, trackerLogo: TrackerLogo, index: Int): FrameLayout {
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.CENTER
        val frameLayoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        if (index == 0) {
            frameLayoutParams.setMargins(25.toPx(), 0, 0, 0)
        } else {
            frameLayoutParams.setMargins((-4).toPx(), 0, 0, 0)
        }
        val frameLayout = FrameLayout(activity)
        frameLayout.alpha = 0f
        frameLayout.visibility = View.INVISIBLE
        frameLayout.id = View.generateViewId()
        frameLayout.layoutParams = frameLayoutParams
        frameLayout.setBackgroundResource(R.drawable.background_tracker_logo)

        val animationView = ImageView(activity)
        val animatedDrawable = AnimatedVectorDrawableCompat.create(activity, R.drawable.network_cross_anim)
        animationView.setImageDrawable(animatedDrawable)
        animationView.layoutParams = params

        val textView = AppCompatTextView(activity)
        textView.gravity = Gravity.CENTER
        textView.setBackgroundResource(trackerLogo.resId)
        TextViewCompat.setTextAppearance(textView, R.style.UnknownTrackerText)
        textView.text = trackerLogo.trackerLetter
        textView.layoutParams = params

        frameLayout.addView(textView)
        frameLayout.addView(animationView)

        return frameLayout
    }

    private fun createTrackerImageLogo(activity: Activity, trackerLogo: TrackerLogo, index: Int): FrameLayout {
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        val frameLayoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        if (index == 0) {
            frameLayoutParams.setMargins(25.toPx(), 0, 0, 0)
        } else {
            frameLayoutParams.setMargins((-4).toPx(), 0, 0, 0)
        }
        params.gravity = Gravity.CENTER
        val frameLayout = FrameLayout(activity)
        frameLayout.alpha = 0f
        frameLayout.visibility = View.INVISIBLE
        frameLayout.id = View.generateViewId()
        frameLayout.layoutParams = frameLayoutParams
        frameLayout.setBackgroundResource(R.drawable.background_tracker_logo)

        val imageView = ImageView(activity)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        val animatedDrawable = AnimatedVectorDrawableCompat.create(activity, R.drawable.network_cross_anim)
        imageView.setImageDrawable(animatedDrawable)
        imageView.setBackgroundResource(trackerLogo.resId)
        imageView.id = View.generateViewId()
        imageView.layoutParams = params

        frameLayout.addView(imageView)

        return frameLayout
    }

    private fun createResourcesIdList(activity: Activity, entities: List<Entity>): List<TrackerLogo> {
        if (activity.packageName == null) return emptyList()
        val resourcesList = entities
            .distinct()
            .take(MAX_LOGOS_SHOWN + 1)
            .map {
                val res = TrackersRenderer().networkLogoIcon(activity, it.name)
                if (res == null) {
                    TrackerLogo(R.drawable.other_tracker_bg, it.displayName.take(1))
                } else {
                    TrackerLogo(res)
                }
            }
            .toMutableList()

        return if (resourcesList.size <= MAX_LOGOS_SHOWN) {
            resourcesList
        } else {
            resourcesList.take(MAX_LOGOS_SHOWN - 1)
                .toMutableList()
                .apply { add(TrackerLogo(R.drawable.ic_more_trackers)) }
        }
    }

    private fun animateBlockedLogos(views: List<View>) {
        views.map {
            if (it is ImageView) {
                val animatedVectorDrawableCompat = it.drawable as? AnimatedVectorDrawableCompat
                animatedVectorDrawableCompat?.start()
            }
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
        container: ViewGroup,
        omnibarViews: List<View>,
        logoViews: List<View>,
        grade: PrivacyGrade?
    ): AnimatorSet {
        return AnimatorSet().apply {
            play(createPartialTrackersAnimation(container, omnibarViews, logoViews))
            play(animateOmnibarMoveToLeft(logoViews))
                .after(TRACKER_LOGOS_DELAY_ON_SCREEN)
                .before(animateOmnibarIn(omnibarViews))
            addListener(
                onEnd = {
                    Timber.d("MARCOS DO ON END")
                    container.removeAllViews()
                    listener?.onAnimationFinished()
                }
            )
        }
    }

//    private fun createFullTrackersAnimation(
//        container: ViewGroup,
//        omnibarViews: List<View>,
//        logoViews: List<View>
//    ): AnimatorSet {
//        return AnimatorSet().apply {
//            play(createPartialTrackersAnimation(container, omnibarViews, logoViews))
//            play(animateFadeOut(container))
//                .after(TRACKER_LOGOS_DELAY_ON_SCREEN)
//                .before(animateOmnibarIn(omnibarViews))
//            addListener(
//                onEnd = {
//                    Timber.d("MARCOS DO ON END")
//                    container.removeViews(1, container.children.count() - 1)
//                }
//            )
//        }
//    }

    private fun createPartialTrackersAnimation(
        container: ViewGroup,
        omnibarViews: List<View>,
        logoViews: List<View>
    ): AnimatorSet {
        val fadeOmnibarOut = animateOmnibarOut(omnibarViews)
        //val fadeLogosIn = animateOmnibarMoveToRight(logoViews)
        val fadeLogosIn = animateOmnibarMoveToRight(logoViews)
        animateMoveToRight(container, logoViews)
        //animateMoveToRight(container, logoViews)
        //applyConstraintSet(container, logoViews)
        animateBlockedLogos(logoViews)

        return AnimatorSet().apply {
            //play(fadeLogosIn).after(fadeOmnibarOut)
            play(fadeOmnibarOut)
        }
    }

//    private fun applyConstraintSet(container: ConstraintLayout, views: List<View>) {
//        val constraints = ConstraintSet()
//        constraints.clone(container)
//
//        views.mapIndexed { index, view ->
//            constraints.connect(view.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
//            constraints.connect(view.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
//            if (index == 0) {
//                constraints.connect(view.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
//            } else {
//                constraints.connect(view.id, ConstraintSet.START, views[index - 1].id, ConstraintSet.END, 10)
//            }
//            if (index == views.size - 1) {
//                constraints.connect(view.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
//            }
//        }
//
//        val viewIds = views.map { it.id }.toIntArray()
//
//        if (viewIds.size > 1) {
//            constraints.createHorizontalChain(
//                ConstraintSet.PARENT_ID,
//                ConstraintSet.LEFT,
//                ConstraintSet.PARENT_ID,
//                ConstraintSet.RIGHT,
//                viewIds,
//                null,
//                ConstraintSet.CHAIN_SPREAD
//            )
//        }
//
//        constraints.applyTo(container)
//    }

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

    private fun animateOmnibarMoveToRight(views: List<View>): AnimatorSet {
//        val animators = views.map {
//            animateMoveToRight2(it)
//        }
        val animators2 = views.map {
            animateFadeIn(it)
        }
        return AnimatorSet().apply {
            playTogether(animators2)
        }
    }

    private fun animateOmnibarMoveToLeft(views: List<View>): AnimatorSet {
        val animators = views.map {
            animateMoveToLeft(it)
        }
        val animators2 = views.map {
            animateFadeOut(it)
        }
        return AnimatorSet().apply {
            playTogether(animators + animators2)
        }
    }

    private fun animateMoveToRight2(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "x", view.left.toFloat(), view.left.toFloat()).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    private fun animateMoveToLeft(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "x", 0f).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    private fun animateMoveToRight(viewGroup: ViewGroup, views: List<View>) {
        val transitionSet = TransitionSet()
        val transitionSlide = Slide(Gravity.START)
        transitionSlide.duration = DEFAULT_ANIMATION_DURATION
        transitionSlide.interpolator = LinearInterpolator()

        val transitionFadeIn = Fade(Fade.IN)
        transitionFadeIn.duration = DEFAULT_ANIMATION_DURATION
        transitionFadeIn.interpolator = LinearInterpolator()

        transitionSet.ordering = TransitionSet.ORDERING_TOGETHER

        transitionSet
            .addTransition(transitionFadeIn)
            .addTransition(transitionSlide)

        TransitionManager.beginDelayedTransition(viewGroup, transitionSet)
        views.map {
            it.visibility = View.VISIBLE
            it.alpha = 1f
        }

    }

    fun pulseAnimation(view: View) {
        if (!pulseAnimation.isRunning) {
            val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 0.8f, 1f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 0.8f, 1f)
            )
            scaleDown.repeatCount = ObjectAnimator.INFINITE
            scaleDown.duration = 750

            pulseAnimation = AnimatorSet().apply {
                play(scaleDown)
                start()
            }
        }
    }

    fun stopPulseAnimation() {
        if (pulseAnimation.isRunning) {
            pulseAnimation.cancel()
        }
    }

    private fun animateFadeOut(view: View, durationInMs: Long = DEFAULT_ANIMATION_DURATION): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            duration = durationInMs
        }
    }

    private fun animateFadeIn(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    private fun animateExpand(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    companion object {
        private const val TRACKER_LOGOS_DELAY_ON_SCREEN = 2400L
        private const val DEFAULT_ANIMATION_DURATION = 150L
        private const val MAX_LOGOS_SHOWN = 4
    }
}

data class TrackerLogo(val resId: Int, val trackerLetter: String = "")