/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.v2

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ViewSyncV2HeaderBinding

internal class SyncHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val binding = ViewSyncV2HeaderBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
    }

    fun setState(
        isSyncEnabled: Boolean,
        isDuckAiAvailable: Boolean,
    ) {
        binding.image.setImageResource(if (isSyncEnabled) R.drawable.ic_sync_enabled_header else R.drawable.ic_sync_disabled_header)
        binding.headlineText.setText(if (isSyncEnabled) R.string.sync_v2_header_headline_enabled else R.string.sync_v2_header_headline_disabled)
        binding.statusIndicator.setStatus(isOn = isSyncEnabled)
        binding.bodyText.setText(
            when {
                isSyncEnabled && isDuckAiAvailable -> R.string.sync_v2_header_body_enabled_with_duck_ai
                isSyncEnabled && !isDuckAiAvailable -> R.string.sync_v2_header_body_enabled
                !isSyncEnabled && isDuckAiAvailable -> R.string.sync_v2_header_body_disabled_with_duck_ai
                else -> R.string.sync_v2_header_body_disabled
            },
        )
    }
}
