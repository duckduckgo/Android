/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.settings

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.IntentCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.RenderMode
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityFireAnimationBinding
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.clear.OnboardingExperimentFireAnimationHelper
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.setAndPropagateUpFitsSystemWindows
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class FireAnimationActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles

    @Inject
    lateinit var onboardingExperimentFireAnimationHelper: OnboardingExperimentFireAnimationHelper

    private val binding: ActivityFireAnimationBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val fireAnimationSerializable = IntentCompat.getSerializableExtra(intent, FIRE_ANIMATION_EXTRA, FireAnimation::class.java)

        if (fireAnimationSerializable == null) finish()

        configureFireAnimationView(fireAnimationSerializable as FireAnimation, binding.fireAnimationView)

        binding.fireAnimationView.show()
        binding.fireAnimationView.playAnimation()
    }

    private fun configureFireAnimationView(
        fireAnimation: FireAnimation,
        fireAnimationView: LottieAnimationView,
    ) {
        if (onboardingDesignExperimentToggles.buckOnboarding().isEnabled()) {
            val resId = onboardingExperimentFireAnimationHelper.getSelectedFireAnimationResId(fireAnimation)
            fireAnimationView.setAnimation(resId)
        } else {
            fireAnimationView.setAnimation(fireAnimation.resId)
        }
        fireAnimationView.setRenderMode(RenderMode.SOFTWARE)
        fireAnimationView.enableMergePathsForKitKatAndAbove(true)
        fireAnimationView.setAndPropagateUpFitsSystemWindows(false)
        fireAnimationView.addAnimatorUpdateListener(accelerateAnimatorUpdateListener)
        fireAnimationView.addAnimatorListener(
            object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                    overridePendingTransition(0, R.anim.tab_anim_fade_out)
                }
            },
        )
    }

    private val accelerateAnimatorUpdateListener = object : ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            binding.fireAnimationView.speed += ANIMATION_SPEED_INCREMENT
            if (binding.fireAnimationView.speed > ANIMATION_MAX_SPEED) {
                binding.fireAnimationView.removeUpdateListener(this)
            }
        }
    }

    companion object {
        const val FIRE_ANIMATION_EXTRA = "FIRE_ANIMATION_EXTRA"

        private const val ANIMATION_MAX_SPEED = 1.4f
        private const val ANIMATION_SPEED_INCREMENT = 0.15f

        fun intent(
            context: Context,
            fireAnimation: FireAnimation,
        ): Intent {
            val intent = Intent(context, FireAnimationActivity::class.java)
            intent.putExtra(FIRE_ANIMATION_EXTRA, fireAnimation)
            return intent
        }
    }
}
