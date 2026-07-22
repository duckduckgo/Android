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
import androidx.constraintlayout.widget.ConstraintLayout
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ViewSyncV2RecoveryCodeBinding
import com.duckduckgo.mobile.android.R as CommonR

internal class RecoveryCodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val binding = ViewSyncV2RecoveryCodeBinding.inflate(LayoutInflater.from(context), this)

    init {
        setBackgroundResource(R.drawable.background_dashed_border)
        val horizontalPadding = 20.toPx()
        val verticalPadding = resources.getDimensionPixelSize(CommonR.dimen.keyline_3)
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
    }

    fun setRecoveryCode(code: String?) {
        binding.recoveryCode.text = code
    }
}
