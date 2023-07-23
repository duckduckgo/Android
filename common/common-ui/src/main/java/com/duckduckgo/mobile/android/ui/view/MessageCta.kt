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

package com.duckduckgo.mobile.android.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.duckduckgo.mobile.android.databinding.ViewMessageCtaBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class MessageCta : FrameLayout {

    private val binding: ViewMessageCtaBinding by viewBinding()

    private var onCloseButton: () -> Unit = {
        this.gone()
    }
    private var onPrimaryButtonClicked: () -> Unit = {}
    private var onSecondaryButtonClicked: () -> Unit = {}
    private var onActionButtonClicked: () -> Unit = {}

    constructor(context: Context) : this(context, null)

    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : this(
        context,
        attrs,
        0,
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int,
    ) : super(context, attrs, defStyle) {

        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        binding.close.setOnClickListener {
            onCloseButton.invoke()
        }

        binding.primaryActionButton.setOnClickListener {
            onPrimaryButtonClicked.invoke()
        }

        binding.secondaryActionButton.setOnClickListener {
            onSecondaryButtonClicked.invoke()
        }

        binding.actionButton.setOnClickListener {
            onActionButtonClicked.invoke()
        }
    }

    fun setMessage(message: Message) {
        when {
            message.topIllustration != null -> {
                binding.middleIllustration.gone()
                binding.topIllustration.show()
                val drawable = AppCompatResources.getDrawable(context, message.topIllustration)
                binding.topIllustration.setImageDrawable(drawable)
            }
            message.middleIllustration != null -> {
                binding.topIllustration.gone()
                binding.middleIllustration.show()
                val drawable = AppCompatResources.getDrawable(context, message.middleIllustration)
                binding.middleIllustration.setImageDrawable(drawable)
            }
            else -> {
                binding.topIllustration.gone()
                binding.middleIllustration.gone()
            }
        }

        binding.messageTitle.text = message.title
        binding.messageSubtitle.text = message.subtitle

        if (message.singleAction.isEmpty()) {
            binding.actionButton.gone()
        } else {
            binding.actionButton.text = message.singleAction
            binding.actionButton.show()
        }

        if (message.action2.isEmpty()) {
            binding.secondaryActionButton.gone()
        } else {
            binding.secondaryActionButton.text = message.action2
            binding.secondaryActionButton.show()
        }

        if (message.action.isEmpty()) {
            binding.primaryActionButton.gone()
        } else {
            binding.primaryActionButton.text = message.action
            binding.primaryActionButton.show()
        }
    }

    fun onPrimaryActionClicked(onPrimaryButtonClicked: () -> Unit) {
        this.onPrimaryButtonClicked = onPrimaryButtonClicked
    }

    fun onSecondaryActionClicked(onSecondaryButtonClicked: () -> Unit) {
        this.onSecondaryButtonClicked = onSecondaryButtonClicked
    }

    fun onActionClicked(onActionClicked: () -> Unit) {
        this.onActionButtonClicked = onActionClicked
    }

    fun onCloseButtonClicked(onDismiss: () -> Unit) {
        this.onCloseButton = onDismiss
    }

    data class Message(
        @DrawableRes val topIllustration: Int? = null,
        @DrawableRes val middleIllustration: Int? = null,
        val title: String = "",
        val subtitle: String = "",
        val action: String = "",
        val action2: String = "",
        val singleAction: String = "",
    )
}
