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

package com.duckduckgo.browser.ui.promo

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.CompoundButton
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.duckduckgo.browser.ui.R
import com.duckduckgo.browser.ui.databinding.ViewPromoMessageBinding
import com.duckduckgo.common.ui.view.addBottomShadow
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.getDimensionFromAttr
import com.google.android.material.card.MaterialCardView
import com.duckduckgo.mobile.android.R as CommonR

class PromoMessageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialCardView(context, attrs, defStyleAttr) {
    private val binding = ViewPromoMessageBinding.inflate(LayoutInflater.from(context), this)

    @DrawableRes
    var headerImage: Int? = null
        set(value) {
            field = value
            binding.headerImage.isVisible = value != null
            value?.let(binding.headerImage::setImageResource)
        }

    var title: CharSequence?
        get() = binding.titleText.text
        set(value) {
            binding.titleText.text = value
            binding.titleText.isVisible = value != null
        }

    var message: CharSequence?
        get() = binding.messageText.text
        set(value) {
            binding.messageText.text = value
            binding.messageText.isVisible = value != null
        }

    var actionStyle: ActionStyle = ActionStyle.Buttons
        set(value) {
            field = value
            updateActionsVisibility()
        }

    var primaryButtonText: CharSequence?
        get() = binding.primaryButton.text
        set(value) {
            binding.primaryButton.text = value
            binding.primaryButton.isVisible = value != null
            updateActionsVisibility()
        }

    var secondaryButtonText: CharSequence?
        get() = binding.secondaryButton.text
        set(value) {
            binding.secondaryButton.text = value
            binding.secondaryButton.isVisible = value != null
            updateActionsVisibility()
        }

    var toggleText: CharSequence? = null
        set(value) {
            field = value
            binding.toggleItem.setPrimaryText(value)
        }

    private var onToggleClick: (() -> Unit)? = null

    // We never show the switch as on: turning it on only starts the setup, and Sync being enabled removes the promotion.
    private val toggleListener: CompoundButton.OnCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, _ ->
        binding.toggleItem.quietlySetIsChecked(false, toggleListener)
        onToggleClick?.invoke()
    }

    var showCloseButton: Boolean
        get() = binding.closeButton.isVisible
        set(value) {
            binding.closeButton.isVisible = value
        }

    var showElevation: Boolean = false
        set(value) {
            field = value
            if (value) addBottomShadow() else elevation = 0f
        }

    @ColorInt
    var cardColor: Int = context.getColorFromAttr(CommonR.attr.daxColorSurfaceSecondary)
        set(value) {
            field = value
            setCardBackgroundColor(value)
        }

    init {
        radius = context.getDimensionFromAttr(CommonR.attr.daxContainerRadius, CommonR.dimen.largeShapeCornerRadius)
        cardElevation = 0f
        binding.toggleContainer.clipToOutline = true
        binding.toggleItem.apply {
            setClickListener { onToggleClick?.invoke() }
            setOnCheckedChangeListener(toggleListener)
        }

        context.withStyledAttributes(attrs, R.styleable.PromoMessageView) {
            headerImage = getResourceId(R.styleable.PromoMessageView_promoHeaderImage, 0).takeIf { it != 0 }
            title = getText(R.styleable.PromoMessageView_promoTitleText)
            message = getText(R.styleable.PromoMessageView_promoMessageText)
            primaryButtonText = getText(R.styleable.PromoMessageView_promoPrimaryButtonText)
            secondaryButtonText = getText(R.styleable.PromoMessageView_promoSecondaryButtonText)
            toggleText = getText(R.styleable.PromoMessageView_promoToggleText)
            actionStyle = ActionStyle.entries[getInt(R.styleable.PromoMessageView_promoActionStyle, ActionStyle.Buttons.ordinal)]
            showCloseButton = getBoolean(R.styleable.PromoMessageView_promoShowCloseButton, true)
            showElevation = getBoolean(R.styleable.PromoMessageView_promoShowElevation, true)
            cardColor = getColor(R.styleable.PromoMessageView_promoCardColor, cardColor)
        }
    }

    fun onPrimaryButtonClicked(onClick: () -> Unit) {
        binding.primaryButton.setOnClickListener { onClick() }
    }

    fun onSecondaryButtonClicked(onClick: () -> Unit) {
        binding.secondaryButton.setOnClickListener { onClick() }
    }

    fun onToggleClicked(onClick: () -> Unit) {
        onToggleClick = onClick
    }

    fun onCloseButtonClicked(onClick: () -> Unit) {
        binding.closeButton.setOnClickListener { onClick() }
    }

    private fun updateActionsVisibility() {
        val hasButtons = binding.primaryButton.isVisible || binding.secondaryButton.isVisible
        binding.buttons.isVisible = actionStyle == ActionStyle.Buttons && hasButtons
        binding.toggleContainer.isVisible = actionStyle == ActionStyle.Toggle
    }

    enum class ActionStyle {
        Buttons,
        Toggle,
    }
}
