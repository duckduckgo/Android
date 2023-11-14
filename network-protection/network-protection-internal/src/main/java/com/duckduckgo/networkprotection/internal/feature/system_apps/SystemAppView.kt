/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.internal.feature.system_apps

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.networkprotection.internal.databinding.SystemAppViewBinding

class SystemAppView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: SystemAppViewBinding by viewBinding()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.systemAppItem.setOnCheckedChangeListener { _, isEnable ->
            systemAppClickListener?.onViewClicked(this, isEnable)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        binding.root.setOnClickListener(null)
    }

    var systemAppClickListener: SystemAppListener? = null

    var systemAppPackageName: String
        @Deprecated("Write only property.", level = DeprecationLevel.ERROR)
        get() = throw NotImplementedError()
        set(value) {
            binding.systemAppItem.setPrimaryText(value)
        }

    var isChecked: Boolean
        @Deprecated("Write only property.", level = DeprecationLevel.ERROR)
        get() = throw NotImplementedError()
        set(value) {
            binding.systemAppItem.setIsChecked(value)
        }

    interface SystemAppListener {
        fun onViewClicked(
            view: View,
            enabled: Boolean,
        )
    }
}
