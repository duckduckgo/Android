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

package com.duckduckgo.mobile.android.themepreview.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.duckduckgo.mobile.android.databinding.ActivityAppComponentsBinding
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.applyTheme
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class AppComponentsActivity : AppCompatActivity() {
    private val binding: ActivityAppComponentsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        val themePreferences = AppComponentsSharedPreferences(this)
        val selectedTheme = themePreferences.selectedTheme
        applyTheme(selectedTheme)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.tabLayout.setupWithViewPager(binding.viewPager)
        val adapter = AppComponentsPagerAdapter(this, supportFragmentManager)
        binding.viewPager.adapter = adapter

        binding.darkThemeSwitch.quietlySetIsChecked(selectedTheme == DuckDuckGoTheme.DARK) { _, enabled ->
            themePreferences.selectedTheme =
                if (enabled) {
                    DuckDuckGoTheme.DARK
                } else {
                    DuckDuckGoTheme.LIGHT
                }
            startActivity(intent(this))
            finish()
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AppComponentsActivity::class.java)
        }
    }
}

class AppComponentsSharedPreferences(private val context: Context) {
    var selectedTheme: DuckDuckGoTheme
        get() {
            return if (preferences.getBoolean(KEY_SELECTED_DARK_THEME, false)) {
                DuckDuckGoTheme.DARK
            } else {
                DuckDuckGoTheme.LIGHT
            }
        }
        set(theme) =
            preferences.edit { putBoolean(KEY_SELECTED_DARK_THEME, theme == DuckDuckGoTheme.DARK) }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.app.dev_settings_activity.theme_settings"
        const val KEY_SELECTED_DARK_THEME = "KEY_SELECTED_DARK_THEME"
    }
}
