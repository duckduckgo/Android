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

package com.duckduckgo.mobile.android.themepreview.ui.component

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.mobile.android.R
import com.google.android.material.snackbar.Snackbar

sealed class ComponentViewHolder(val view: View) : RecyclerView.ViewHolder(view) {

    open fun bind(component: Component) {
        // Override in subclass if needed.
    }

    class ButtonComponentViewHolder(parent: ViewGroup) :
        ComponentViewHolder(inflate(parent, R.layout.component_buttons))

    class TopAppBarComponentViewHolder(parent: ViewGroup) :
        ComponentViewHolder(inflate(parent, R.layout.component_top_app_bar))

    class SwitchComponentViewHolder(parent: ViewGroup) :
        ComponentViewHolder(inflate(parent, R.layout.component_switch))

    class RadioButtonComponentViewHolder(parent: ViewGroup) :
        ComponentViewHolder(inflate(parent, R.layout.component_radio_button))

    class CheckboxComponentViewHolder(parent: ViewGroup) :
        ComponentViewHolder(inflate(parent, R.layout.component_checkbox))

    class InfoPanelComponentViewHolder(
        parent: ViewGroup
    ) : ComponentViewHolder(inflate(parent, R.layout.component_info_panel))

    class SearchBarComponentViewHolder(
        parent: ViewGroup
    ) : ComponentViewHolder(inflate(parent, R.layout.component_search_bar))

    class MenuItemComponentViewHolder(
        parent: ViewGroup
    ) : ComponentViewHolder(inflate(parent, R.layout.component_menu_item))

    class SingleLineItemComponentViewHolder(
        parent: ViewGroup
    ) : ComponentViewHolder(inflate(parent, R.layout.component_single_line_item))

    class TwoLineItemComponentViewHolder(
        parent: ViewGroup
    ) : ComponentViewHolder(inflate(parent, R.layout.component_two_line_item)){

    }

    @SuppressLint("ShowToast")
    class SnackbarComponentViewHolder(parent: ViewGroup) :
        ComponentViewHolder(inflate(parent, R.layout.component_snackbar)) {

        init {
            val container: FrameLayout = view.findViewById(R.id.snackbar_container)
            val snackbarView =
                Snackbar.make(container, "This is a Snackbar message", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Action") {}
                    .view
            (snackbarView.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER

            container.addView(snackbarView)
        }
    }

    companion object {
        fun create(
            parent: ViewGroup,
            viewType: Int
        ): ComponentViewHolder {
            return when (Component.values()[viewType]) {
                Component.BUTTON -> ButtonComponentViewHolder(parent)
                Component.TOP_APP_BAR -> TopAppBarComponentViewHolder(parent)
                Component.SWITCH -> SwitchComponentViewHolder(parent)
                Component.RADIO_BUTTON -> RadioButtonComponentViewHolder(parent)
                Component.CHECKBOX -> CheckboxComponentViewHolder(parent)
                Component.SNACKBAR -> SnackbarComponentViewHolder(parent)
                Component.INFO_PANEL -> InfoPanelComponentViewHolder(parent)
                Component.SEARCH_BAR -> SearchBarComponentViewHolder(parent)
                Component.MENU_ITEM -> MenuItemComponentViewHolder(parent)
                Component.SINGLE_LINE_LIST_ITEM -> SingleLineItemComponentViewHolder(parent)
                Component.TWO_LINE_LIST_ITEM -> TwoLineItemComponentViewHolder(parent)
                else -> {
                    TODO()
                }
            }
        }

        private fun inflate(
            parent: ViewGroup,
            layout: Int
        ): View {
            return LayoutInflater.from(parent.context).inflate(layout, parent, false)
        }
    }
}
