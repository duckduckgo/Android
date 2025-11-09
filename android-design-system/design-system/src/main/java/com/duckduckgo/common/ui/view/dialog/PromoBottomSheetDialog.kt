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

package com.duckduckgo.common.ui.view.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.mobile.android.databinding.BottomSheetPromoBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

@SuppressLint("NoBottomSheetDialog")
class PromoBottomSheetDialog(builder: Builder) : BottomSheetDialog(builder.context) {

    abstract class EventListener {
        /** Sets a listener to be invoked when the bottom sheet is shown */
        open fun onBottomSheetShown() {}

        /** Sets a listener to be invoked when the bottom sheet is dismiss */
        open fun onBottomSheetDismissed() {}

        /** Sets a listener to be invoked when primary button is clicked */
        open fun onPrimaryButtonClicked() {}

        /** Sets a listener to be invoked when secondary button is clicked */
        open fun onSecondaryButtonClicked() {}
    }

    internal class DefaultEventListener : EventListener()

    private val binding: BottomSheetPromoBinding = BottomSheetPromoBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)

        setOnDismissListener { builder.listener.onBottomSheetDismissed() }
        setOnShowListener { builder.listener.onBottomSheetShown() }
        binding.bottomSheetPromoPrimaryButton.setOnClickListener {
            builder.listener.onPrimaryButtonClicked()
            setOnDismissListener(null)
            dismiss()
        }
        binding.bottomSheetPromoSecondaryButton.setOnClickListener {
            builder.listener.onSecondaryButtonClicked()
            setOnDismissListener(null)
            dismiss()
        }

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

    /**
     * Creates a builder for a promo bottom sheet dialog that uses
     * the default bottom sheet dialog theme.
     *
     * @param context the parent context
     */
    class Builder(val context: Context) {
        var listener: EventListener = DefaultEventListener()
            private set
        var icon: Int? = null
            private set
        var titleText: String? = null
            private set
        var contentText: String = ""
            private set
        var primaryButtonText: String = ""
            private set
        var secondaryButtonText: String = ""
            private set

        /** Sets event listener for the bottom sheet dialog */
        fun addEventListener(eventListener: EventListener): Builder {
            listener = eventListener
            return this
        }

        /** Sets side image for the bottom sheet dialog (optional) */
        fun setIcon(@DrawableRes iconRes: Int): Builder {
            icon = iconRes
            return this
        }

        /** Sets title text for the bottom sheet dialog (optional) */
        fun setTitle(text: String): Builder {
            titleText = text
            return this
        }

        /** Sets content text for the bottom sheet dialog */
        fun setContent(text: String): Builder {
            contentText = text
            return this
        }

        /** Sets primary button text for the bottom sheet dialog */
        fun setPrimaryButton(text: String): Builder {
            primaryButtonText = text
            return this
        }

        /** Sets secondary button text for the bottom sheet dialog */
        fun setSecondaryButton(text: String): Builder {
            secondaryButtonText = text
            return this
        }

        /** Start the dialog and display it on screen */
        fun show() {
            val dialog = PromoBottomSheetDialog(this)
            dialog.show()
        }

        fun build(): PromoBottomSheetDialog {
            return PromoBottomSheetDialog(this)
        }
    }
}
