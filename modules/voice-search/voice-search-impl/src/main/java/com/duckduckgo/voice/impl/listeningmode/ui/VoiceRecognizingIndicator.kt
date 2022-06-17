/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.voice.impl.listeningmode.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.util.AttributeSet
import android.view.ViewPropertyAnimator
import android.widget.FrameLayout
import com.duckduckgo.voice.impl.listeningmode.ui.VoiceRecognizingIndicator.Action
import com.duckduckgo.voice.impl.listeningmode.ui.VoiceRecognizingIndicator.Action.INDICATOR_CLICKED
import com.duckduckgo.voice.impl.listeningmode.ui.VoiceRecognizingIndicator.Model
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.voice.impl.listeningmode.OnDeviceSpeechRecognizer.Companion.MAX_VOLUME
import com.duckduckgo.voice.impl.listeningmode.OnDeviceSpeechRecognizer.Companion.MIN_VOLUME
import com.duckduckgo.voice.impl.databinding.ViewVoiceRecognizingIndicatorBinding
import java.util.*

interface VoiceRecognizingIndicator {
    fun bind(model: Model)
    fun onAction(actionHandler: (Action) -> Unit)
    fun destroy()

    data class Model(
        val volume: Float
    )

    enum class Action {
        INDICATOR_CLICKED
    }
}

class VoiceRecognizingIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), VoiceRecognizingIndicator {

    companion object {
        private const val PULSE_MIN_RADIUS = 0.6f
        private const val PULSE_MAX_RADIUS = 0.9f
        private const val PULSE_DURATION = 2500L
        private const val VOLUME_UPDATE_DURATION = 100L
        private const val MAX_SPEECH_RADIUS = 1.3f
        private const val MIN_SPEECH_RADIUS = 1f
        private const val RADIUS_DIFF = (MAX_SPEECH_RADIUS - MIN_SPEECH_RADIUS) / (MAX_VOLUME - MIN_VOLUME)
    }

    private val binding: ViewVoiceRecognizingIndicatorBinding by viewBinding()
    private val pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
        binding.pulse,
        PropertyValuesHolder.ofFloat("scaleX", PULSE_MIN_RADIUS, PULSE_MAX_RADIUS),
        PropertyValuesHolder.ofFloat("scaleY", PULSE_MIN_RADIUS, PULSE_MAX_RADIUS),
    ).apply {
        duration = PULSE_DURATION
        repeatCount = ObjectAnimator.INFINITE
        repeatMode = ObjectAnimator.REVERSE
    }

    private val reversePulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
        binding.pulse,
        PropertyValuesHolder.ofFloat("scaleX", PULSE_MAX_RADIUS, PULSE_MIN_RADIUS),
        PropertyValuesHolder.ofFloat("scaleY", PULSE_MAX_RADIUS, PULSE_MIN_RADIUS),
    ).apply {
        duration = PULSE_DURATION
        repeatCount = ObjectAnimator.INFINITE
        repeatMode = ObjectAnimator.REVERSE
    }

    private val animatorSet = AnimatorSet()
    private val volumes = LinkedList<Float>()
    private var isAnimating = false
    private var setVolumeViewPropertyAnimator: ViewPropertyAnimator? = null
    private var resetVolumeViewPropertyAnimator: ViewPropertyAnimator? = null

    init {
        binding.pulse.visibility = VISIBLE
        animatorSet.play(pulseAnimator)
        animatorSet.start()
    }

    override fun bind(model: Model) {
        animatorSet.cancel()
        animatorSet.childAnimations.clear()
        binding.pulse.clearAnimation()
        val newRadius = model.volume.toRadius()
        volumes.push(newRadius)

        if (!isAnimating) {
            isAnimating = true
            runAnimation(volumes.pop())
        }
    }

    private fun runAnimation(newRadius: Float) {
        setVolumeViewPropertyAnimator = binding.pulse.animate()
            .setDuration(VOLUME_UPDATE_DURATION)
            .scaleY(newRadius)
            .scaleX(newRadius)
            .withEndAction {
                resetVolumeViewPropertyAnimator = binding.pulse.animate()
                    .setDuration(VOLUME_UPDATE_DURATION)
                    .scaleY(PULSE_MAX_RADIUS)
                    .scaleX(PULSE_MAX_RADIUS)
                    .withEndAction {
                        if (volumes.isNotEmpty()) {
                            runAnimation(volumes.pop())
                        } else {
                            isAnimating = false
                            animatorSet.play(reversePulseAnimator)
                            animatorSet.start()
                        }
                    }
            }
    }

    override fun onAction(actionHandler: (Action) -> Unit) {
        binding.microphone.setOnClickListener { actionHandler.invoke(INDICATOR_CLICKED) }
    }

    override fun destroy() {
        binding.pulse.clearAnimation()
        setVolumeViewPropertyAnimator?.cancel()
        resetVolumeViewPropertyAnimator?.cancel()
        animatorSet.end()
        animatorSet.childAnimations.clear()
    }

    private fun Float.toRadius(): Float {
        return MIN_SPEECH_RADIUS + (this * RADIUS_DIFF)
    }
}
