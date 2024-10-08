/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.databinding.ViewMessageInfoDisabledBinding
import com.duckduckgo.mobile.android.vpn.databinding.ViewMessageInfoEnabledBinding

class AppTpEnabledInfoPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewMessageInfoEnabledBinding by viewBinding()

    fun setClickableLink(
        annotation: String,
        fullText: CharSequence,
        onClick: () -> Unit,
    ) {
        binding.root.setClickableLink(annotation, fullText, onClick)
    }
}

class AppTpDisabledInfoPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewMessageInfoDisabledBinding by viewBinding()

    fun setClickableLink(
        annotation: String,
        fullText: CharSequence,
        onClick: () -> Unit,
    ) {
        binding.root.setClickableLink(annotation, fullText, onClick)
    }
}
