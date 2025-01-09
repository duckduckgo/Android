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
import android.content.Context
import android.content.res.Resources
import android.view.Gravity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo
import com.duckduckgo.app.browser.omnibar.animations.TrackersLottieAssetDelegate
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(FragmentScope::class)
class LottieTrackersCircleAnimationHelper @Inject constructor() : TrackersCircleAnimationHelper {

    private lateinit var trackersCircleAnimationView: LottieAnimationView
    private lateinit var omnibarShieldAnimationView: LottieAnimationView
    private lateinit var resources: Resources

    override fun startTrackersCircleAnimation(
        context: Context,
        trackersCircleAnimationView: LottieAnimationView,
        omnibarShieldAnimationView: LottieAnimationView,
        omnibarPosition: OmnibarPosition,
        logos: List<TrackerLogo>,
    ) {
        this.trackersCircleAnimationView = trackersCircleAnimationView
        this.omnibarShieldAnimationView = omnibarShieldAnimationView
        this.resources = context.resources

        val negativeMarginPx = (-72).toPx()
        val gravity = if (omnibarPosition == OmnibarPosition.BOTTOM) Gravity.BOTTOM else Gravity.NO_GRAVITY
        val layoutParams = trackersCircleAnimationView.layoutParams as CoordinatorLayout.LayoutParams
        layoutParams.gravity = gravity
        layoutParams.marginStart = negativeMarginPx
        if (gravity == Gravity.BOTTOM) {
            trackersCircleAnimationView.scaleY = -1f
        } else {
            layoutParams.topMargin = negativeMarginPx
            trackersCircleAnimationView.scaleY = 1f
        }
        trackersCircleAnimationView.setLayoutParams(layoutParams)

        omnibarShieldAnimationView.setAnimation(R.raw.protected_shield)

        with(trackersCircleAnimationView) {
            this.setCacheComposition(false)
            this.setAnimation(getNewAnimationRawRes(logos))
            this.maintainOriginalImageBounds = true
            this.setImageAssetDelegate(TrackersLottieAssetDelegate(context, logos))
            this.removeAllAnimatorListeners()
            this.show()

            this.addAnimatorListener(
                object : AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {
                        this@LottieTrackersCircleAnimationHelper.trackersCircleAnimationView.show()
                        this@LottieTrackersCircleAnimationHelper.omnibarShieldAnimationView.setMaxProgress(1f)
                        this@LottieTrackersCircleAnimationHelper.omnibarShieldAnimationView.playAnimation()
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        this@LottieTrackersCircleAnimationHelper.trackersCircleAnimationView.gone()
                    }

                    override fun onAnimationCancel(animation: Animator) {}

                    override fun onAnimationRepeat(animation: Animator) {}
                },
            )

            this.setMaxProgress(1f)
            this.playAnimation()
        }
    }

    private fun getNewAnimationRawRes(logos: List<TrackerLogo>): Int {
        val trackers = logos.size
        // We need 3 different json files for 1, 2 and 3+ trackers.
        return when {
            trackers == 1 -> R.raw.shieldburst
            trackers == 2 -> R.raw.shieldburst
            trackers >= 3 -> R.raw.shieldburst
            else -> R.raw.shieldburst
        }
    }
}
