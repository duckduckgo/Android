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
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme.DARK_V2
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme.LIGHT
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme.LIGHT_V2
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

        binding.darkThemeSwitch.quietlySetIsChecked(selectedTheme == DuckDuckGoTheme.DARK || selectedTheme == DARK_V2) { _, enabled ->
            themePreferences.selectedTheme = getTheme(enabled, binding.newDesignSystemSwitch.isSwitchChecked())
            startActivity(intent(this))
            finish()
        }

        binding.newDesignSystemSwitch.quietlySetIsChecked(selectedTheme == DuckDuckGoTheme.DARK_V2 || selectedTheme == LIGHT_V2) { _, enabled ->
            themePreferences.selectedTheme = getTheme(binding.darkThemeSwitch.isSwitchChecked(), enabled)
            startActivity(intent(this))
            finish()
        }
    }

    private fun applyLocalTheme(theme: DuckDuckGoTheme) {
        when (theme) {
            LIGHT_V2 -> setTheme(R.style.Theme_DuckDuckGo_Light)
            DARK_V2 -> setTheme(R.style.Theme_DuckDuckGo_Dark)
            LIGHT -> setTheme(R.style.AppTheme_Light)
            DARK -> setTheme(R.style.AppTheme_Light)
            else -> setTheme(R.style.AppTheme_Light)
        }
    }

    private fun getTheme(
        darkThemeEnabled: Boolean,
        newDesignSystemEnabled: Boolean
    ): DuckDuckGoTheme {
        return if (darkThemeEnabled) {
            if (newDesignSystemEnabled) {
                DARK_V2
            } else {
                DARK
            }
        } else {
            if (newDesignSystemEnabled) {
                LIGHT_V2
            } else {
                LIGHT
            }
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
            val theme = preferences.getString(KEY_SELECTED_THEME, DARK_V2.name)
            return DARK_V2
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
