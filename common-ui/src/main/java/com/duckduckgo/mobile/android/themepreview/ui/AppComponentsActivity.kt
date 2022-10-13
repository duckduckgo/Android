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
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ActivityAppComponentsBinding
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme.DARK
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme.LIGHT
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class AppComponentsActivity : AppCompatActivity() {

    private val binding: ActivityAppComponentsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        val themePreferences = AppComponentsSharedPreferences(this)
        val selectedTheme = themePreferences.selectedTheme
        applyLocalTheme(selectedTheme)

        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.tabLayout.setupWithViewPager(binding.viewPager)
        binding.viewPager.adapter = AppComponentsPagerAdapter(this, supportFragmentManager)

        binding.darkThemeSwitch.quietlySetIsChecked(selectedTheme == DuckDuckGoTheme.DARK) { _, enabled ->
            themePreferences.selectedTheme = getTheme(enabled)
            startActivity(intent(this))
            finish()
        }
    }

    private fun applyLocalTheme(theme: DuckDuckGoTheme) {
        when (theme) {
            DARK -> setTheme(R.style.Theme_DuckDuckGo_Dark)
            else -> setTheme(R.style.Theme_DuckDuckGo_Light)
        }
    }

    private fun getTheme(
        darkThemeEnabled: Boolean
    ): DuckDuckGoTheme {
        return if (darkThemeEnabled) {
            DARK
        } else {
            LIGHT
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
            return when (preferences.getString(KEY_SELECTED_THEME, DARK.name)) {
                DARK.name -> DARK
                else -> LIGHT
            }
        }
        set(theme) =
            preferences.edit { putString(KEY_SELECTED_THEME, theme.name) }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.app.dev_settings_activity.theme_settings"
        const val KEY_SELECTED_THEME = "KEY_SELECTED_THEME"
    }
}
