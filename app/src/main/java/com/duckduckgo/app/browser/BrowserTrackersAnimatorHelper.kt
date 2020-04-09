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
import android.app.Activity
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.DaxDialogCta
import com.duckduckgo.app.privacy.renderer.TrackersRenderer
import com.duckduckgo.app.trackerdetection.model.TrackingEvent

class BrowserTrackersAnimatorHelper {

    private var trackersAnimation: AnimatorSet = AnimatorSet()

    fun startTrackersAnimation(cta: Cta?, activity: Activity, container: ConstraintLayout, omnibarViews: List<View>, events: List<TrackingEvent>?) {
        if (events.isNullOrEmpty()) return

        val logoViews: List<View> = getLogosViewListInContainer(activity, container, events)
        if (logoViews.isEmpty()) return

        if (!trackersAnimation.isRunning) {
            trackersAnimation = if (cta is DaxDialogCta.DaxTrackersBlockedCta) {
                createPartialTrackersAnimation(container, omnibarViews, logoViews).apply {
                    start()
                }
            } else {
                createFullTrackersAnimation(container, omnibarViews, logoViews).apply {
                    start()
                }
            }
        }
    }

    fun cancelAnimations() {
        if (trackersAnimation.isRunning) {
            trackersAnimation.end()
        }
    }

    fun finishTrackerAnimation(fadeInViews: List<View>, container: ConstraintLayout) {
        val animateOmnibarIn = animateOmnibarIn(fadeInViews)
        val animateLogosOut = animateFadeOut(container)
        trackersAnimation = AnimatorSet().apply {
            play(animateLogosOut).before(animateOmnibarIn)
            start()
        }
    }

    private fun getLogosViewListInContainer(activity: Activity, container: ConstraintLayout, events: List<TrackingEvent>): List<View> {
        container.removeAllViews()
        container.alpha = 0f
        val logos = createResourcesIdList(activity, events)
        return createLogosViewList(activity, container, logos)
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

    private fun createResourcesIdList(activity: Activity, events: List<TrackingEvent>): List<Int>? {
        if (activity.packageName == null) return emptyList()
        val resourcesList = events.asSequence()
            .mapNotNull { it.entity }
            .sortedByDescending { it.prevalence }
            .map { TrackersRenderer().networkLogoIcon(activity, it.name) }
            .filterNotNull()
            .distinct()
            .toMutableList()

        return if (resourcesList.size <= MAX_LOGOS_SHOWN) {
            resourcesList.take(MAX_LOGOS_SHOWN)
        } else {
            resourcesList.take(MAX_LOGOS_SHOWN - 1)
                .toMutableList()
                .apply { add(R.drawable.ic_more_trackers) }
        }
    }

    private fun animateBlockedLogos(views: List<View>) {
        views.map {
            if (it is ImageView) {
                val frameAnimation = it.drawable as AnimatedVectorDrawable
                frameAnimation.start()
            }
        }
    }

    private fun createFullTrackersAnimation(
        container: ConstraintLayout,
        omnibarViews: List<View>,
        logoViews: List<View>
    ): AnimatorSet {
        return AnimatorSet().apply {
            play(createPartialTrackersAnimation(container, omnibarViews, logoViews))
            play(animateFadeOut(container))
                .after(TRACKER_LOGOS_DELAY_ON_SCREEN)
                .before(animateOmnibarIn(omnibarViews))
        }
    }

    private fun createPartialTrackersAnimation(
        container: ConstraintLayout,
        omnibarViews: List<View>,
        logoViews: List<View>
    ): AnimatorSet {
        val fadeOmnibarOut = animateOmnibarOut(omnibarViews)
        val fadeLogosIn = animateFadeIn(container)

        applyConstraintSet(container, logoViews)
        animateBlockedLogos(logoViews)

        return AnimatorSet().apply {
            play(fadeLogosIn).after(fadeOmnibarOut)
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

        constraints.applyTo(container)
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

    private fun animateFadeOut(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            duration = DEFAULT_ANIMATION_DURATION
        }
    }

    private fun animateFadeIn(view: View): ObjectAnimator {
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