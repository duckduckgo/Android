/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.setup

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ViewSyncSetupCtaBinding

class SyncSetupCta @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.syncSetupCtaStyle,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewSyncSetupCtaBinding by viewBinding()

    private var onPrimaryButtonClicked: () -> Unit = {}
    private var onSecondaryButtonClicked: () -> Unit = {}

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.SyncSetupCta,
            0,
            R.style.Widget_DuckDuckGo_SyncSetupCta,
        ).apply {
            setLeadingIconDrawable(getDrawable(R.styleable.SyncSetupCta_leadingIcon)!!)
            setPrimaryText(getString(R.styleable.SyncSetupCta_primaryText))
            setSecondaryText(getString(R.styleable.SyncSetupCta_secondaryText))

            if (hasValue(R.styleable.SyncSetupCta_primaryCta)){
                setPrimaryButtonText(getString(R.styleable.SyncSetupCta_primaryCta))
            } else {
                hidePrimaryButton()
            }

            if (hasValue(R.styleable.SyncSetupCta_secondaryCta)){
                setSecondaryButtonText(getString(R.styleable.SyncSetupCta_secondaryCta))
            } else {
                hideSecondaryButton()
            }

            recycle()
        }
    }

    /** Sets the primary text */
    fun setPrimaryText(primaryText: String?) {
        binding.messageTitle.text = primaryText
    }

    /** Sets the secondary text */
    fun setSecondaryText(secondaryText: String?) {
        binding.messageSubtitle.text = secondaryText
    }

    /** Sets the primary button text */
    fun setPrimaryButtonText(primaryButtonText: String?) {
        binding.primaryActionButton.text = primaryButtonText
    }

    /** Sets the secondary button text */
    fun setSecondaryButtonText(secondaryButtonText: String?) {
        binding.secondaryActionButton.text = secondaryButtonText
    }

    /** Sets the leading icon image drawable */
    fun setLeadingIconDrawable(drawable: Drawable) {
        binding.topIllustration.setImageDrawable(drawable)
    }

    /** Hides Primary Button */
    fun hidePrimaryButton() {
        binding.primaryActionButton.gone()
    }

    /** Hides Secondary Button */
    fun hideSecondaryButton() {
        binding.secondaryActionButton.gone()
    }

    /** Set Primary Button click listener */
    fun onPrimaryActionClicked(onPrimaryButtonClicked: () -> Unit) {
        binding.primaryActionButton.setOnClickListener { onPrimaryButtonClicked() }
    }

    /** Set Secondary Button click listener */
    fun onSecondaryActionClicked(onSecondaryButtonClicked: () -> Unit) {
        binding.secondaryActionButton.setOnClickListener { onSecondaryButtonClicked() }
    }
}
