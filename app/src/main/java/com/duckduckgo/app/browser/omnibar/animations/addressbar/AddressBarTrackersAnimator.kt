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

package com.duckduckgo.app.browser.omnibar.animations.addressbar

import android.animation.Animator
import android.animation.AnimatorSet
import android.content.Context
import android.transition.Scene
import android.transition.Slide
import android.transition.Transition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.core.transition.addListener
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import javax.inject.Inject

class AddressBarTrackersAnimator @Inject constructor(
    private val trackerCountAnimator: TrackerCountAnimator,
    private val commonAddressBarAnimationHelper: CommonAddressBarAnimationHelper,
) {
    var isAnimationRunning = false
        private set

    private val runningAnimators = mutableListOf<Animator>()
    private var sceneRoot: ViewGroup? = null
    private var animatedIconBackgroundView: View? = null
    private var addressBarTrackersBlockedAnimationShieldIcon: LottieAnimationView? = null
    private var onAnimationComplete: (() -> Unit)? = null

    fun startAnimation(
        context: Context,
        sceneRoot: ViewGroup,
        animatedIconBackgroundView: View,
        addressBarTrackersBlockedAnimationShieldIcon: LottieAnimationView,
        omnibarViews: List<View>,
        shieldViews: List<View>,
        entities: List<Entity>?,
        onAnimationComplete: () -> Unit,
    ) {
        if (isAnimationRunning) return

        if (entities.isNullOrEmpty()) {
            onAnimationComplete()
            return
        }

        isAnimationRunning = true

        this.sceneRoot = sceneRoot
        this.animatedIconBackgroundView = animatedIconBackgroundView
        this.addressBarTrackersBlockedAnimationShieldIcon = addressBarTrackersBlockedAnimationShieldIcon
        this.onAnimationComplete = onAnimationComplete

        addressBarTrackersBlockedAnimationShieldIcon.show()
        addressBarTrackersBlockedAnimationShieldIcon.progress = 0F

        val slideInTrackersTransition: Transition = createSlideTransition()
        val slideOutTrackersTransition: Transition = createSlideTransition()

        val inflater = LayoutInflater.from(context)
        val scene1Layout = inflater.inflate(R.layout.address_bar_trackers_animation_scene_1, sceneRoot, false)
        val scene2Layout = inflater.inflate(R.layout.address_bar_trackers_animation_scene_2, sceneRoot, false)

        val trackersBlockedText = context.resources.getQuantityString(
            R.plurals.trackersBlockedAnimationMessage,
            entities.size,
        )

        val trackersBlockedTextViewScene1 = scene1Layout.findViewById<DaxTextView>(R.id.trackersBlockedTextView)
        val trackersBlockedCountTextViewScene1 = scene1Layout.findViewById<DaxTextView>(R.id.trackersBlockedCountView)
        trackersBlockedTextViewScene1.text = trackersBlockedText

        val trackersBlockedTextViewScene2 = scene2Layout.findViewById<DaxTextView>(R.id.trackersBlockedTextView)
        val trackersBlockedCountTextViewScene2 = scene2Layout.findViewById<DaxTextView>(R.id.trackersBlockedCountView)
        trackersBlockedTextViewScene2.text = trackersBlockedText

        val trackerAnimationStartCountText = trackerCountAnimator.getTrackerAnimationStartCount(entities.size).toString()
        trackersBlockedCountTextViewScene1.text = trackerAnimationStartCountText
        trackersBlockedCountTextViewScene2.text = trackerAnimationStartCountText

        val scene1 = Scene(sceneRoot, scene1Layout)
        val scene2 = Scene(sceneRoot, scene2Layout)

        slideInTrackersTransition.addListener(
            onStart = {
                AnimatorSet().apply {
                    play((commonAddressBarAnimationHelper.animateFadeIn(trackersBlockedTextViewScene2, TRACKERS_ANIMATION_TEXT_FADE_IN_DURATION)))
                        .with(
                            commonAddressBarAnimationHelper.animateFadeIn(
                                trackersBlockedCountTextViewScene2,
                                TRACKERS_ANIMATION_TEXT_FADE_IN_DURATION,
                            ),
                        )
                        .with(
                            commonAddressBarAnimationHelper.animateFadeIn(
                                animatedIconBackgroundView,
                                CommonAddressBarAnimationHelper.DEFAULT_ANIMATION_DURATION,
                            ),
                        )
                    runningAnimators.add(this)
                    start()
                }
            },
            onEnd = {
                trackerCountAnimator.animateTrackersBlockedCountView(
                    context = context,
                    totalTrackerCount = entities.size,
                    trackerTextView = trackersBlockedCountTextViewScene2,
                    onAnimationEnd = {
                        AnimatorSet().apply {
                            startDelay = TRACKERS_ANIMATION_SLIDE_OUT_DELAY
                            addListener(
                                onEnd = {
                                    TransitionManager.go(scene1, slideOutTrackersTransition)
                                },
                            )
                            runningAnimators.add(this)
                            start()
                        }
                    },
                )
            },
        )

        slideOutTrackersTransition.addListener(
            onStart = {
                AnimatorSet().apply {
                    play(commonAddressBarAnimationHelper.animateFadeOut(trackersBlockedTextViewScene2, TRACKERS_ANIMATION_TEXT_FADE_OUT_DURATION))
                        .with(
                            commonAddressBarAnimationHelper.animateFadeOut(
                                trackersBlockedCountTextViewScene2,
                                TRACKERS_ANIMATION_TEXT_FADE_OUT_DURATION,
                            ),
                        )
                    runningAnimators.add(this)
                    start()
                }
            },
            onEnd = {
                AnimatorSet().apply {
                    play(commonAddressBarAnimationHelper.animateViewsIn(omnibarViews + shieldViews))
                    play(
                        commonAddressBarAnimationHelper.animateFadeOut(
                            animatedIconBackgroundView,
                            CommonAddressBarAnimationHelper.Companion.DEFAULT_ANIMATION_DURATION,
                        ),
                    )
                    addListener(
                        onEnd = {
                            addressBarTrackersBlockedAnimationShieldIcon.addAnimatorListener(
                                object : Animator.AnimatorListener {
                                    override fun onAnimationStart(p0: Animator) {}

                                    override fun onAnimationEnd(p0: Animator) {
                                        addressBarTrackersBlockedAnimationShieldIcon.gone()
                                        addressBarTrackersBlockedAnimationShieldIcon.progress = 0F
                                        isAnimationRunning = false
                                        onAnimationComplete()
                                    }

                                    override fun onAnimationCancel(p0: Animator) {}

                                    override fun onAnimationRepeat(p0: Animator) {}
                                },
                            )
                            addressBarTrackersBlockedAnimationShieldIcon.playAnimation()
                        },
                    )
                    runningAnimators.add(this)
                    start()
                }
                sceneRoot.gone()
            },
        )

        AnimatorSet().apply {
            play(commonAddressBarAnimationHelper.animateViewsOut(omnibarViews))
            addListener(
                onEnd = {
                    sceneRoot.show()
                    sceneRoot.alpha = 1F
                    TransitionManager.go(scene2, slideInTrackersTransition)
                },
            )
            runningAnimators.add(this)
            start()
        }
    }

    fun cancelAnimation() {
        if (!isAnimationRunning) return

        trackerCountAnimator.cancelAnimation()
        runningAnimators.forEach { animator ->
            animator.cancel()
        }
        runningAnimators.clear()

        sceneRoot?.let {
            TransitionManager.endTransitions(it)
            it.gone()
        }

        animatedIconBackgroundView?.let {
            it.alpha = 0f
        }

        addressBarTrackersBlockedAnimationShieldIcon?.let {
            it.cancelAnimation()
            it.removeAllAnimatorListeners()
            it.gone()
            it.progress = 0F
        }

        isAnimationRunning = false

        sceneRoot = null
        animatedIconBackgroundView = null
        addressBarTrackersBlockedAnimationShieldIcon = null
        onAnimationComplete = null
    }

    private fun createSlideTransition(): Transition {
        val slideInTrackersTransition: Transition = Slide(Gravity.START)
        slideInTrackersTransition.duration = ANIMATION_DURATION
        return slideInTrackersTransition
    }

    companion object {
        private const val ANIMATION_DURATION = 300L
        private const val TRACKERS_ANIMATION_SLIDE_OUT_DELAY = 1000L
        private const val TRACKERS_ANIMATION_TEXT_FADE_IN_DURATION = 900L
        private const val TRACKERS_ANIMATION_TEXT_FADE_OUT_DURATION = 300L
    }
}
