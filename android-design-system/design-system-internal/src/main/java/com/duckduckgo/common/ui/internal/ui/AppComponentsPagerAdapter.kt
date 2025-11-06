/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.internal.ui

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.duckduckgo.common.ui.internal.ui.component.ComponentOtherFragment
import com.duckduckgo.common.ui.internal.ui.component.buttons.ComponentButtonsFragment
import com.duckduckgo.common.ui.internal.ui.component.buttons.ComponentInteractiveElementsFragment
import com.duckduckgo.common.ui.internal.ui.component.cards.ComponentLayoutsFragment
import com.duckduckgo.common.ui.internal.ui.component.listitems.ComponentListItemsElementsFragment
import com.duckduckgo.common.ui.internal.ui.component.navigation.ComponentMessagingFragment
import com.duckduckgo.common.ui.internal.ui.component.textinput.ComponentTextInputFragment
import com.duckduckgo.common.ui.internal.ui.dialogs.DialogsFragment
import com.duckduckgo.common.ui.internal.ui.palette.ColorPaletteFragment
import com.duckduckgo.common.ui.internal.ui.typography.TypographyFragment
import com.duckduckgo.mobile.android.R

/** View pager to show all tabbed destinations - Instructions, Theme Summary and Components. */
class AppComponentsPagerAdapter(
    private val context: Context,
    fragmentManager: FragmentManager,
) :
    FragmentStatePagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    enum class MainFragments(val titleRes: Int) {
        PALETTE(R.string.tab_title_color_palette),
        TYPOGRAPHY(R.string.tab_title_typography),
        BUTTONS(R.string.tab_title_buttons),
        TEXT_INPUT(R.string.tab_title_text_input),
        DIALOGS(R.string.tab_title_dialogs),
        LAYOUTS(R.string.tab_title_layouts),
        INTERACTIVE_ELEMENTS(R.string.tab_title_component_interactive),
        MESSAGING(R.string.tab_title_component_messaging),
        LIST_ITEMS(R.string.tab_title_component_list_items),
        OTHERS(R.string.tab_title_component_others),
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
            MainFragments.BUTTONS -> ComponentButtonsFragment()
            MainFragments.TEXT_INPUT -> ComponentTextInputFragment()
            MainFragments.DIALOGS -> DialogsFragment()
            MainFragments.LAYOUTS -> ComponentLayoutsFragment()
            MainFragments.INTERACTIVE_ELEMENTS -> ComponentInteractiveElementsFragment()
            MainFragments.MESSAGING -> ComponentMessagingFragment()
            MainFragments.LIST_ITEMS -> ComponentListItemsElementsFragment()
            MainFragments.OTHERS -> ComponentOtherFragment()
        }
    }
}
