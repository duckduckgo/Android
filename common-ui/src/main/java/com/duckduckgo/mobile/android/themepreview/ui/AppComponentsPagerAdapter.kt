/*
 * Copyright 2019 The Android Open Source Project
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.themepreview.ui.component.buttons.ComponentButtonsFragment
import com.duckduckgo.mobile.android.themepreview.ui.component.controls.ComponentControlsFragment
import com.duckduckgo.mobile.android.themepreview.ui.component.navigation.ComponentNavigationFragment
import com.duckduckgo.mobile.android.themepreview.ui.component.system.ComponentSystemFragment
import com.duckduckgo.mobile.android.themepreview.ui.palette.ColorPaletteFragment
import com.duckduckgo.mobile.android.themepreview.ui.typography.TypographyFragment

/**
 * View pager to show all tabbed destinations - Instructions, Theme Summary and Components.
 */
class AppComponentsPagerAdapter(
    private val context: Context,
    fragmentManager: FragmentManager
) : FragmentStatePagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    enum class MainFragments(val titleRes: Int) {
        PALETTE(R.string.tab_title_color_palette),
        TYPOGRAPHY(R.string.tab_title_typography),
        NAVIGATION(R.string.tab_title_component_navigation),
        BUTTONS(R.string.tab_title_component_buttons),
        CONTROLS(R.string.tab_title_component_controls),
        SYSTEM(R.string.tab_title_component_system),
    }

    override fun getCount(): Int = MainFragments.values().size

    private fun getItemType(position: Int): MainFragments {
        return MainFragments.values()[position]
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return context.getString(getItemType(position).titleRes)
    }

    override fun getItem(position: Int): Fragment {
        return when (getItemType(position)) {
            MainFragments.PALETTE -> ColorPaletteFragment()
            MainFragments.TYPOGRAPHY -> TypographyFragment()
            MainFragments.NAVIGATION -> ComponentNavigationFragment()
            MainFragments.BUTTONS -> ComponentButtonsFragment()
            MainFragments.CONTROLS -> ComponentControlsFragment()
            MainFragments.SYSTEM -> ComponentSystemFragment()
        }
    }
}
