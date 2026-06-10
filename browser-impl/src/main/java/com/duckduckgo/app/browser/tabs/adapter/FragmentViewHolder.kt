/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.duckduckgo.app.browser.tabs.adapter

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

/**
 * [ViewHolder] implementation for handling [Fragment]s. Used in [FragmentStateAdapter].
 */
abstract class FragmentViewHolder(container: FrameLayout) : RecyclerView.ViewHolder(container) {
    val container: FrameLayout
        get() = itemView as FrameLayout
}

class FrameLayoutViewHolder private constructor(container: FrameLayout) : FragmentViewHolder(container) {
    companion object {
        @JvmStatic
        fun create(parent: ViewGroup): FragmentViewHolder {
            val container = FrameLayout(parent.context)
            container.setLayoutParams(
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            container.id = View.generateViewId()
            container.isSaveEnabled = false
            return FrameLayoutViewHolder(container)
        }
    }
}
