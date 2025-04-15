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

package com.duckduckgo.app.browser.animations

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(FragmentScope::class)
class LottieExperimentTrackersAnimationHelper @Inject constructor() : ExperimentTrackersAnimationHelper {

    private var omnibarShieldAnimationView: LottieAnimationView? = null

    private val conflatedJob = ConflatedJob()

    override fun startShieldPopAnimation(
        omnibarShieldAnimationView: LottieAnimationView,
        trackersCountAndBlockedViews: List<DaxTextView>,
        omnibarTextInput: View,
    ) {
        this.omnibarShieldAnimationView = omnibarShieldAnimationView

        with(omnibarShieldAnimationView) {
            this.removeAllAnimatorListeners()
            this.addAnimatorListener(
                object : AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {
                        animateViews(trackersCountAndBlockedViews, omnibarTextInput, 500L)
                    }

                    override fun onAnimationEnd(animation: Animator) {}

                    override fun onAnimationCancel(animation: Animator) {}

                    override fun onAnimationRepeat(animation: Animator) {}
                },
            )

            this.setAnimation(R.raw.protected_shield_experiment)
            this.setMaxProgress(1f)
            this.playAnimation()
        }
    }

    override fun cancelAnimations() {
        this.omnibarShieldAnimationView?.cancelAnimation()
        conflatedJob.cancel()
    }

    private fun animateViews(
        trackersCountAndBlockedViews: List<DaxTextView>,
        omnibarTextInput: View,
        animationDuration: Long,
    ) {
        val animations = mutableListOf<ValueAnimator>()
        trackersCountAndBlockedViews.forEach {
            animations.add(
                ValueAnimator.ofFloat(1f, 0f).apply {
                    removeAllListeners()
                    addUpdateListener { animation ->
                        val alpha = animation.animatedValue as Float
                        it.alpha = alpha
                    }
                    duration = 200L
                    interpolator = AccelerateDecelerateInterpolator()
                },
            )
        }

        animations.add(
            ValueAnimator.ofFloat(0f, 1f).apply {
                removeAllListeners()
                addUpdateListener { animation ->
                    val alpha = animation.animatedValue as Float
                    omnibarTextInput.alpha = alpha
                }
                duration = 200L
                interpolator = AccelerateDecelerateInterpolator()
            },
        )

        val delayAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
        }

        AnimatorSet().apply {
            playSequentially(
                delayAnimator,
                AnimatorSet().apply {
                    playTogether(animations.toList())
                    removeAllListeners()
                    addListener(
                        object : AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                // Reset everything.
                                trackersCountAndBlockedViews.forEach {
                                    it.text = ""
                                    it.gone()
                                    it.alpha = 1f
                                }
                            }

                            override fun onAnimationCancel(animation: Animator) {}
                            override fun onAnimationRepeat(animation: Animator) {}
                        },
                    )
                },
            )
            start()
        }
    }
}
