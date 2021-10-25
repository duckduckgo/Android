/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.email

import android.content.Context
import android.os.Bundle
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentAutofillTooltipBinding
import com.duckduckgo.app.global.view.html
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class EmailAutofillTooltipFragment(
    context: Context,
    val address: String
) : BottomSheetDialog(context, R.style.EmailTooltip) {

    private val binding: ContentAutofillTooltipBinding by viewBinding()

    var useAddress: (() -> Unit) = {}
    var usePrivateAlias: (() -> Unit) = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setDialog()
    }

    private fun setDialog() {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        val addressFormatted = context.getString(R.string.autofillTooltipUseYourAlias, address)
        binding.tooltipPrimaryCtaTitle.text = addressFormatted.html(context)

        binding.secondaryCta.setOnClickListener {
            usePrivateAlias()
            dismiss()
        }

        binding.primaryCta.setOnClickListener {
            useAddress()
            dismiss()
        }
    }
}
