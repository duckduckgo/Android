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
import com.duckduckgo.adblocking.impl.databinding.BottomSheetAdBlockingMenuBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

@SuppressLint("NoBottomSheetDialog")
class AdBlockingMenuBottomSheetDialog(builderContext: Context) : BottomSheetDialog(builderContext) {

    interface EventListener {
        fun onChoiceSelected(choice: AdBlockingChoice)
    }

    var eventListener: EventListener? = null

    private val binding: BottomSheetAdBlockingMenuBinding =
        BottomSheetAdBlockingMenuBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)

        binding.adBlockingMenuCloseButton.setOnClickListener { dismiss() }
        binding.adBlockingMenuAlwaysOn.setOnClickListener { onChoiceSelected(AdBlockingChoice.ALWAYS_ON) }
        binding.adBlockingMenuDisableUntilRelaunch.setOnClickListener {
            onChoiceSelected(AdBlockingChoice.DISABLE_UNTIL_RELAUNCH)
        }
        binding.adBlockingMenuAlwaysOff.setOnClickListener { onChoiceSelected(AdBlockingChoice.ALWAYS_OFF) }
    }

    private fun onChoiceSelected(choice: AdBlockingChoice) {
        eventListener?.onChoiceSelected(choice)
        dismiss()
    }
}
