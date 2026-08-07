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

package com.duckduckgo.subscriptions.impl.onboarding.features

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.withStyledAttributes
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ViewFeatureInfoListItemBinding

/**
 * A single feature highlight on a subscription onboarding feature-info screen: a rounded card with a
 * leading icon, an [R.attr.featureInfoTitle] title and an [R.attr.featureInfoDescription] description.
 *
 * Items that also list supported platforms nest a [PlatformIconsView] (optionally preceded by a divider)
 * as a child of this view in XML; those children render below the description inside the same card.
 */
class FeatureInfoListItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewFeatureInfoListItemBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.background_feature_info_list_item)
        val padding = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_4)
        setPadding(padding, padding, padding, padding)

        context.withStyledAttributes(attrs, R.styleable.FeatureInfoListItemView) {
            getResourceId(R.styleable.FeatureInfoListItemView_featureInfoIcon, 0).takeIf { it != 0 }?.let(::setIcon)
            getText(R.styleable.FeatureInfoListItemView_featureInfoTitle)?.let(binding.featureInfoItemTitle::setText)
            getText(R.styleable.FeatureInfoListItemView_featureInfoDescription)?.let(binding.featureInfoItemDescription::setText)
        }
    }

    fun setIcon(@DrawableRes iconRes: Int) {
        binding.featureInfoItemIcon.setImageResource(iconRes)
    }

    fun setTitle(@StringRes title: Int) {
        binding.featureInfoItemTitle.setText(title)
    }

    fun setDescription(@StringRes description: Int) {
        binding.featureInfoItemDescription.setText(description)
    }
}
