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

package com.duckduckgo.app.accessibility

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.accessibility.AccessibilityScreens.Default
import com.duckduckgo.app.accessibility.AccessibilityScreens.HighlightedItem
import com.duckduckgo.app.browser.databinding.ActivityAccessibilitySettingsBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.quietlySetValue
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.getActivityParams
import com.google.android.material.slider.Slider
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat
import java.text.NumberFormat

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(Default::class, screenName = "accessibility")
@ContributeToActivityStarter(HighlightedItem::class, screenName = "accessibility")
class AccessibilityActivity : DuckDuckGoActivity() {

    private val binding: ActivityAccessibilitySettingsBinding by viewBinding()
    private val viewModel: AccessibilitySettingsViewModel by bindViewModel()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val systemFontSizeChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onSystemFontSizeChanged(isChecked)
    }

    private val forceZoomChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onForceZoomChanged(isChecked)
    }

    private val fontSizeChangeListener = Slider.OnChangeListener { _, newValue, _ ->
        viewModel.onFontSizeChanged(newValue)
    }

    private val voiceSearchChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onVoiceSearchChanged(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)
        observeViewModel()
        scrollToHighlightedItem()
    }

    private fun scrollToHighlightedItem() {
        intent.getActivityParams(HighlightedItem::class.java)?.let { params ->
            if (params.highlightedItem == VOICE_SEARCH) {
                binding.voiceSearchToggle.post {
                    scrollToVoiceSearchToggle()
                    highlightVoiceSearchToggle()
                }
            }
        }
    }

    private fun scrollToVoiceSearchToggle() {
        val scrollTo = binding.voiceSearchToggle.top
        binding.scrollView.smoothScrollTo(0, scrollTo)
    }

    private fun highlightVoiceSearchToggle() {
        val highlightColor = getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorContainer)
        val transparentColor = ContextCompat.getColor(applicationContext, android.R.color.transparent)

        val totalAnimationDuration = FADE_DURATION * TRANSITIONS

        val colorAnimator = ValueAnimator.ofArgb(transparentColor, highlightColor, transparentColor, highlightColor, transparentColor, highlightColor)
        colorAnimator.duration = totalAnimationDuration
        colorAnimator.addUpdateListener { animator ->
            binding.voiceSearchToggle.setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnimator.start()
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                logcat(INFO) { "AccessibilityActSettings: newViewState $viewState" }
                renderFontSize(viewState.appFontSize, viewState.overrideSystemFontSize)
                binding.appFontSizeToggle.quietlySetIsChecked(viewState.overrideSystemFontSize, systemFontSizeChangeListener)
                binding.forceZoomToggle.quietlySetIsChecked(viewState.forceZoom, forceZoomChangeListener)
                if (viewState.showVoiceSearch) {
                    binding.voiceSearchToggle.visibility = View.VISIBLE
                    binding.voiceSearchToggle.quietlySetIsChecked(viewState.voiceSearchEnabled, voiceSearchChangeListener)
                }
            }.launchIn(lifecycleScope)
    }

    private fun renderFontSize(
        fontSize: Float,
        overrideSystemFontSize: Boolean,
    ) {
        logcat(VERBOSE) { "AccessibilityActSettings: renderFontSize $fontSize" }

        binding.accessibilitySlider.quietlySetValue(fontSize, fontSizeChangeListener)
        val newValue = fontSize / 100
        val formatter = NumberFormat.getPercentInstance()
        binding.accessibilitySliderValue.text = formatter.format(newValue)
        // Avoids scaling our Sample Text when overriding system font size
        binding.accessibilityHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16 * newValue)

        binding.fontSizeSettingsGroup.alpha = if (overrideSystemFontSize) 1.0f else 0.40f
        binding.accessibilitySlider.isEnabled = overrideSystemFontSize
    }

    companion object {
        private const val VOICE_SEARCH = "voiceSearch"
        private const val FADE_DURATION = 300L
        private const val TRANSITIONS = 5
    }
}
