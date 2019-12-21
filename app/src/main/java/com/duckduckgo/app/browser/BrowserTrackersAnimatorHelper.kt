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
import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Resources
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.DaxDialogCta
import com.duckduckgo.app.privacy.renderer.TrackersRenderer
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class BrowserTrackersAnimatorHelper @Inject constructor() : CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() + Dispatchers.Main

    private var logosStayOnScreenDuration: Long = TRACKER_LOGOS_DELAY_ON_SCREEN

    private var loadingAnimation: AnimatorSet = AnimatorSet()
    private var trackersAnimation: AnimatorSet = AnimatorSet()
    private var typingAnimationJob: Job? = null

    private fun createScanningAnimation(resources: Resources, loadingText: TextView): Job {
        return launch {
            while (true) {
                if (loadingText.text.contains("...")) {
                    loadingText.text = resources.getString(R.string.trackersAnimationText)
                } else {
                    loadingText.text = resources.getString(R.string.trackersAnimationDotText, loadingText.text)
                }
                delay(SCANNING_DELAY)
            }
        }
    }

    fun createLoadedAnimation(
        cta: Cta?,
        activity: Activity,
        container: ConstraintLayout,
        loadingText: View,
        views: List<View>,
        events: List<TrackingEvent>?
    ) {
        if (loadingAnimation.isRunning) {
            loadingAnimation.end()
        }

        if (!trackersAnimation.isRunning) {
            views.map {
                it.alpha = 0f
            }
            typingAnimationJob?.cancel()
            typingAnimationJob = null
            trackersAnimation = if (cta is DaxDialogCta.DaxTrackersBlockedCta) {
                createTrackersAnimation(activity, container, loadingText, events).apply {
                    start()
                }
            } else {
                createCompleteTrackersAnimation(activity, container, loadingText, views, events).apply {
                    start()
                }
            }
        }
    }

    fun cancelAnimations() {
        typingAnimationJob?.cancel()
        typingAnimationJob = null
        if (loadingAnimation.isRunning) {
            loadingAnimation.end()
        }
        if (trackersAnimation.isRunning) {
            trackersAnimation.end()
        }
    }

    fun createLoadingAnimation(resources: Resources, fadeOutViews: List<View>, fadeInView: View) {
        if (!loadingAnimation.isRunning && !trackersAnimation.isRunning) {

            val animators = fadeOutViews.map {
                animateFadeOut(it)
            }

            val fadeLoadingIn = animateFadeIn(fadeInView)
            val transition = AnimatorSet().apply {
                playTogether(animators)
            }

            loadingAnimation = AnimatorSet().apply {
                play(fadeLoadingIn).after(transition)
                start()
            }

            if (typingAnimationJob == null) {
                if (fadeInView is TextView) {
                    typingAnimationJob = createScanningAnimation(resources, fadeInView)
                }
            }
        }
    }

    fun finishTrackerAnimation(fadeInViews: List<View>, container: ConstraintLayout) {
        val animateOmnibarIn = animateOmnibarIn(fadeInViews)
        val fadeLogosOut = animateFadeOut(container)
        trackersAnimation = AnimatorSet().apply {
            play(fadeLogosOut).before(animateOmnibarIn)
            start()
        }
    }

    private fun createLogosViewList(activity: Activity, container: ConstraintLayout, resourcesId: List<Int>?): List<View> {
        return resourcesId?.map {
            val imageView = ImageView(activity)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setImageResource(R.drawable.network_cross_anim)
            imageView.setBackgroundResource(it)
            imageView.id = View.generateViewId()
            container.addView(imageView)
            return@map imageView
        }.orEmpty()
    }

    private fun createResourcesIdList(activity: Activity, events: List<TrackingEvent>?): List<Int>? {
        val packageName = activity.packageName
        if (events.isNullOrEmpty() || packageName == null) return emptyList()

        return events.asSequence().mapNotNull {
            it.entity
        }.map {
            TrackersRenderer().networkLogoIcon(activity, it.name)
        }.filterNotNull().distinct().take(MAX_LOGOS_SHOWN).toList()
    }

    private fun animateBlockedLogos(views: List<View>) {
        views.map {
            if (it is ImageView) {
                val frameAnimation = it.drawable as AnimatedVectorDrawable
                frameAnimation.start()
            }
        }
    }

    private fun createCompleteTrackersAnimation(
        activity: Activity,
        container: ConstraintLayout,
        loadingText: View,
        views: List<View>,
        events: List<TrackingEvent>?
    ): AnimatorSet {
        return AnimatorSet().apply {
            play(createTrackersAnimation(activity, container, loadingText, events))
            play(animateFadeOut(container))
                .after(logosStayOnScreenDuration)
                .before(animateOmnibarIn(views))
        }
    }

    private fun createTrackersAnimation(
        activity: Activity,
        container: ConstraintLayout,
        loadingText: View,
        events: List<TrackingEvent>?
    ): AnimatorSet {
        container.removeAllViews()
        container.alpha = 0f

        val logos = createResourcesIdList(activity, events)
        val views: List<View> = createLogosViewList(activity, container, logos)

        applyConstraintSet(container, views)

        val fadeLoadingOut = animateFadeOut(loadingText)
        val fadeLogosIn = animateFadeIn(container)

        animateBlockedLogos(views)

        return AnimatorSet().apply {
            play(fadeLogosIn).after(fadeLoadingOut)
        }
    }

    private fun applyConstraintSet(container: ConstraintLayout, views: List<View>) {
        val constraints = ConstraintSet()
        constraints.clone(container)

        views.mapIndexed { index, view ->
            constraints.connect(view.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            constraints.connect(view.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            if (index == 0) {
                constraints.connect(view.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            } else {
                constraints.connect(view.id, ConstraintSet.START, views[index - 1].id, ConstraintSet.END, 10)
            }
            if (index == views.size - 1) {
                constraints.connect(view.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            }
        }

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

        logosStayOnScreenDuration = if (viewIds.isEmpty()) {
            TRACKER_LOGOS_REMOVE_DELAY
        } else {
            TRACKER_LOGOS_DELAY_ON_SCREEN
        }

        constraints.applyTo(container)
    }

    private fun animateOmnibarIn(views: List<View>): AnimatorSet {
        val animators = views.map {
            animateFadeIn(it)
        }
        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    @SuppressLint("ObjectAnimatorBinding")
    fun animateFadeOut(view: Any): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    @SuppressLint("ObjectAnimatorBinding")
    fun animateFadeIn(view: Any): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    companion object {
        private const val TRACKER_LOGOS_REMOVE_DELAY = 0L
        private const val TRACKER_LOGOS_DELAY_ON_SCREEN = 3000L
        private const val DEFAULT_ANIMATION_DURATION = 250L
        private const val MAX_LOGOS_SHOWN = 4
        const val SCANNING_DELAY = 340L
    }
}