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

package com.duckduckgo.adblocking.impl.menu

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import com.duckduckgo.adblocking.impl.databinding.BottomSheetAdBlockingDisabledBinding
import com.duckduckgo.common.ui.applyBottomSystemBarInsetPadding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.duckduckgo.mobile.android.R as CommonR

@SuppressLint("NoBottomSheetDialog")
class AdBlockingDisabledBottomSheetDialog(
    builderContext: Context,
    private val edgeToEdgeProvider: EdgeToEdgeProvider,
    private val brokenSiteReportRequester: BrokenSiteReportRequester,
) : BottomSheetDialog(
    builderContext,
    if (edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.BOTTOM_SHEETS)) {
        CommonR.style.Widget_DuckDuckGo_BottomSheetDialog_EdgeToEdge
    } else {
        0
    },
) {

    private val binding: BottomSheetAdBlockingDisabledBinding =
        BottomSheetAdBlockingDisabledBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)

        if (edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.BOTTOM_SHEETS)) {
            binding.root.applyBottomSystemBarInsetPadding()
        }

        binding.adBlockingDisabledCloseButton.setOnClickListener { dismiss() }
        binding.adBlockingDisabledPrimaryButton.setOnClickListener {
            brokenSiteReportRequester.requestReport()
            dismiss()
        }
        binding.adBlockingDisabledSecondaryButton.setOnClickListener { dismiss() }
    }
}
