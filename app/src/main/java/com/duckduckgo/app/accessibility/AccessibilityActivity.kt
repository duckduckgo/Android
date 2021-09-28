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
import com.duckduckgo.app.browser.databinding.ActivityAccessibilitySettingsBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import timber.log.Timber
import java.text.NumberFormat

class AccessibilityActivity : DuckDuckGoActivity() {

    private val binding: ActivityAccessibilitySettingsBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)
        configureListener()
    }

    private fun configureListener() {
        binding.accessibilitySlider.value = 100f
        binding.accessibilitySlider.addOnChangeListener { _, value, _ ->
            val newValue = value / 100
            val formatter = NumberFormat.getPercentInstance()
            Timber.i("accessibilitySlider slider $value")
            binding.accessibilitySliderValue.text = formatter.format(newValue)
            binding.accessibilityHint.textSize = 16 * newValue
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AccessibilityActivity::class.java)
        }
    }
}
