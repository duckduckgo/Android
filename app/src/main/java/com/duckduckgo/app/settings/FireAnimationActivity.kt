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
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.RenderMode
import com.duckduckgo.app.browser.databinding.ActivityFireAnimationBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.mobile.android.ui.view.setAndPropagateUpFitsSystemWindows
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class FireAnimationActivity : DuckDuckGoActivity() {

    private val binding: ActivityFireAnimationBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val fireAnimationSerializable = intent.getSerializableExtra(FIRE_ANIMATION_EXTRA)

        if (fireAnimationSerializable == null) finish()

        configureFireAnimationView(fireAnimationSerializable as FireAnimation, binding.fireAnimationView)

        binding.fireAnimationView.show()
        binding.fireAnimationView.playAnimation()
    }

    private fun configureFireAnimationView(fireAnimation: FireAnimation, fireAnimationView: LottieAnimationView) {
        fireAnimationView.setAnimation(fireAnimation.resId)
        fireAnimationView.setRenderMode(RenderMode.SOFTWARE)
        fireAnimationView.enableMergePathsForKitKatAndAbove(true)
        fireAnimationView.setAndPropagateUpFitsSystemWindows(false)
        fireAnimationView.addAnimatorUpdateListener(accelerateAnimatorUpdateListener)
        fireAnimationView.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {}
            override fun onAnimationCancel(animation: Animator?) {}
            override fun onAnimationStart(animation: Animator?) {}
            override fun onAnimationEnd(animation: Animator?) {
                finish()
            }
        })
    }

    private val accelerateAnimatorUpdateListener = object : ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator?) {
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

        fun intent(context: Context, fireAnimation: FireAnimation): Intent {
            val intent = Intent(context, FireAnimationActivity::class.java)
            intent.putExtra(FIRE_ANIMATION_EXTRA, fireAnimation)
            return intent
        }
    }
}
