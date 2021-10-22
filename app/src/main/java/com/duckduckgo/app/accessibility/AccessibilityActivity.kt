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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.CompoundButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.databinding.ActivityAccessibilitySettingsBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.view.quietlySetValue
import com.duckduckgo.mobile.android.ui.view.recursiveEnable
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.google.android.material.slider.Slider
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.text.NumberFormat

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)
        configureListener()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                Timber.i("AccessibilityActSettings: newViewState $viewState")
                renderFontSize(viewState.appFontSize, viewState.useSystemFontSize)
                binding.forceZoomToggle.quietlySetIsChecked(viewState.forceZoom, forceZoomChangeListener)
                binding.appFontSizeToggle.quietlySetIsChecked(viewState.useSystemFontSize, systemFontSizeChangeListener)
            }.launchIn(lifecycleScope)
    }

    private fun renderFontSize(fontSize: Float, useSystemFontSize: Boolean) {
        Timber.i("AccessibilityActSettings: renderFontSize $fontSize")
        val enableFontSizeSettings = !useSystemFontSize

        binding.accessibilitySlider.quietlySetValue(fontSize, fontSizeChangeListener)
        val newValue = fontSize / 100
        val formatter = NumberFormat.getPercentInstance()
        binding.accessibilitySliderValue.text = formatter.format(newValue)
        // Avoids scaling our Sample Text when overriding system font size
        binding.accessibilityHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16 * newValue)

        binding.fontSizeSettingsGroup.recursiveEnable(enableFontSizeSettings)
    }

    private fun configureListener() {
        Timber.i("AccessibilityActSettings: configureListener")
        binding.appFontSizeToggle.setOnCheckedChangeListener(systemFontSizeChangeListener)
        binding.accessibilitySlider.addOnChangeListener(fontSizeChangeListener)
        binding.forceZoomToggle.setOnCheckedChangeListener(forceZoomChangeListener)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AccessibilityActivity::class.java)
        }
    }
}
