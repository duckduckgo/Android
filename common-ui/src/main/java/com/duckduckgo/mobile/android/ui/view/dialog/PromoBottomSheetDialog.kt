/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.ui.view.dialog

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.BottomSheetPromoBinding
import com.duckduckgo.mobile.android.ui.view.show
import com.google.android.material.bottomsheet.BottomSheetDialog

class PromoBottomSheetDialog(builder: Builder) : BottomSheetDialog(builder.context, R.style.Widget_DuckDuckGo_BottomSheetDialog) {

    abstract class EventListener {
        open fun onBottomSheetShown() {}
        open fun onBottomSheetDismissed() {}
        open fun onPrimaryButtonClicked() {}
        open fun onSecondaryButtonClicked() {}
    }

    internal class DefaultEventListener : EventListener()

    private val binding: BottomSheetPromoBinding = BottomSheetPromoBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)

        setOnDismissListener { builder.listener.onBottomSheetDismissed() }
        setOnShowListener { builder.listener.onBottomSheetShown() }
        binding.bottomSheetPromoPrimaryButton.setOnClickListener { builder.listener.onPrimaryButtonClicked() }
        binding.bottomSheetPromoSecondaryButton.setOnClickListener { builder.listener.onSecondaryButtonClicked() }

        builder.icon?.let {
            binding.bottomSheetPromoIcon.setImageResource(it)
            binding.bottomSheetPromoIcon.show()
        }

        builder.titleText?.let {
            binding.bottomSheetPromoTitle.text = it
            binding.bottomSheetPromoTitle.show()
        }

        binding.bottomSheetPromoContent.text = builder.contentText
        binding.bottomSheetPromoPrimaryButton.text = builder.primaryButtonText
        binding.bottomSheetPromoSecondaryButton.text = builder.secondaryButtonText
    }

    class Builder(val context: Context) {
        var listener: EventListener = DefaultEventListener()
        var icon: Int? = null
        var titleText: String? = null
        var contentText: String = ""
        var primaryButtonText: String = ""
        var secondaryButtonText: String = ""

        fun addEventListener(eventListener: EventListener): Builder {
            listener = eventListener
            return this
        }

        fun setIcon(@DrawableRes iconRes: Int): Builder {
            icon = iconRes
            return this
        }

        fun setTitle(text: String): Builder {
            titleText = text
            return this
        }

        fun setContent(text: String): Builder {
            contentText = text
            return this
        }

        fun setPrimaryButton(text: String): Builder {
            primaryButtonText = text
            return this
        }

        fun setSecondaryButton(text: String): Builder {
            secondaryButtonText = text
            return this
        }

        fun show() {
            val dialog = PromoBottomSheetDialog(this)
            dialog.show()
        }
    }
}
