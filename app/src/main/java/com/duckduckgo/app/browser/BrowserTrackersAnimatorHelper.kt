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
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import java.util.Locale
import javax.inject.Inject

class BrowserTrackersAnimatorHelper @Inject constructor() {

    var logosStayOnScreenDuration: Long = 3000L

    fun createLoadingAnimation(fadeOutViews: List<View>, fadeInView: View): AnimatorSet {
        logosStayOnScreenDuration = 3000L

        val animators = fadeOutViews.map {
            animateFadeOut(it)
        }

        val fadeLoadingIn = animateFadeIn(fadeInView)
        val transition = AnimatorSet().apply {
            playTogether(animators)
        }

        return AnimatorSet().apply {
            play(fadeLoadingIn).after(transition)
        }
    }

    fun finishTrackerAnimation(fadeInViews: List<View>, container: ConstraintLayout): AnimatorSet {
        val animateOmnibarIn = animateOmnibarIn(fadeInViews)
        val fadeLogosOut = animateFadeOut(container)
        return AnimatorSet().apply {
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
        }.orEmpty().toList()
    }

    private fun createResourcesIdList(activity: Activity, events: List<TrackingEvent>?): List<Int>? {
        val packageName = activity.packageName
        if (events.isNullOrEmpty() || packageName == null) return emptyList()

        return events.asSequence().mapNotNull {
            it.trackerNetwork
        }.map {
            val logoName = "${LOGO_RES_PREFIX}${it.name.toLowerCase(Locale.getDefault()).replace(".", "")}"
            activity.resources.getIdentifier(logoName, "drawable", packageName)
        }.toList().filter { it != 0 }.distinct().take(MAX_LOGOS_SHOWN).toList()
    }

    private fun animateBlockedLogos(views: List<View>) {
        views.map {
            if (it is ImageView) {
                val frameAnimation = it.drawable as AnimatedVectorDrawable
                frameAnimation.start()
            }
        }
    }

    fun createTrackersAnimation(activity: Activity, container: ConstraintLayout, loadingText: View, events: List<TrackingEvent>?): AnimatorSet {
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

        if (viewIds.isEmpty()) {
            logosStayOnScreenDuration = 0L
        }

        constraints.applyTo(container)
    }

    fun animateOmnibarIn(views: List<View>): AnimatorSet {
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
        private const val DEFAULT_ANIMATION_DURATION = 250L
        private const val MAX_LOGOS_SHOWN = 4
        private const val LOGO_RES_PREFIX = "network_logo_"
    }
}