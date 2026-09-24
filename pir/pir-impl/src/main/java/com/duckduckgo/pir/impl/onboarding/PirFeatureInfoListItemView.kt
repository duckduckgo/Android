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

package com.duckduckgo.pir.impl.onboarding

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.withStyledAttributes
import com.duckduckgo.pir.impl.R
import com.duckduckgo.pir.impl.databinding.ViewPirFeatureInfoListItemBinding

class PirFeatureInfoListItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewPirFeatureInfoListItemBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.background_pir_feature_info_list_item)
        val padding = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_4)
        setPadding(padding, padding, padding, padding)

        context.withStyledAttributes(attrs, R.styleable.PirFeatureInfoListItemView) {
            getResourceId(R.styleable.PirFeatureInfoListItemView_featureInfoIcon, 0).takeIf { it != 0 }?.let(::setIcon)
            getText(R.styleable.PirFeatureInfoListItemView_featureInfoTitle)?.let(binding.featureInfoItemTitle::setText)
            getText(R.styleable.PirFeatureInfoListItemView_featureInfoDescription)?.let(binding.featureInfoItemDescription::setText)
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
