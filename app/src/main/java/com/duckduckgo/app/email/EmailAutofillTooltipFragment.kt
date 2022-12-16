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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentAutofillTooltipBinding
import com.duckduckgo.app.global.extensions.html
import com.google.android.material.bottomsheet.BottomSheetDialog

@SuppressLint("NoBottomSheetDialog")
class EmailAutofillTooltipFragment(
    context: Context,
    val address: String,
) : BottomSheetDialog(context) {

    private val binding = ContentAutofillTooltipBinding.inflate(LayoutInflater.from(context))

    var useAddress: (() -> Unit) = {}
    var usePrivateAlias: (() -> Unit) = {}

    init {
        setContentView(binding.root)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDialog()
    }

    private fun setDialog() {
        val addressFormatted = context.getString(R.string.autofillTooltipUseYourAlias, address)
        binding.primaryCta.setPrimaryText(addressFormatted.html(context).toString())

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
