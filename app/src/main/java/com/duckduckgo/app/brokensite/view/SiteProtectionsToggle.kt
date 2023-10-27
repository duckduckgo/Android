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

package com.duckduckgo.app.brokensite.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.duckduckgo.app.brokensite.model.SiteProtectionsState
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewSiteProtectionsToggleBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class SiteProtectionsToggle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewSiteProtectionsToggleBinding by viewBinding()
    private var state = SiteProtectionsState.DISABLED

    fun setState(state: SiteProtectionsState) {
        if (this.state != state) {
            this.state = state
            updateViewState()
        }
    }

    fun setOnProtectionsToggledListener(listener: (Boolean) -> Unit) {
        binding.protectionsSwitch.setOnCheckedChangeListener { _, isChecked ->
            listener.invoke(isChecked)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateViewState()
    }

    private fun updateViewState() = with(binding) {
        when (state) {
            SiteProtectionsState.ENABLED -> {
                protectionsSwitch.isChecked = true
                protectionsSwitch.isEnabled = true
                protectionsSwitchLabel.setText(R.string.brokenSiteProtectionsOn)
                protectionsBannerMessage.setText(R.string.brokenSiteProtectionsOnBannerMessage)
                protectionsBannerMessageContainer.isVisible = true
                protectionsBannerMessageContainer.setBackgroundResource(R.drawable.background_site_protections_toggle_banner_tooltip)
            }
            SiteProtectionsState.DISABLED -> {
                protectionsSwitch.isChecked = false
                protectionsSwitch.isEnabled = true
                protectionsSwitchLabel.setText(R.string.brokenSiteProtectionsOff)
                protectionsBannerMessage.text = null
                protectionsBannerMessageContainer.isVisible = false
                protectionsBannerMessageContainer.background = null
            }
            SiteProtectionsState.DISABLED_BY_REMOTE_CONFIG -> {
                protectionsSwitch.isChecked = false
                protectionsSwitch.isEnabled = false
                protectionsSwitchLabel.setText(R.string.brokenSiteProtectionsOff)
                protectionsBannerMessage.setText(R.string.brokenSiteProtectionsOffByRemoteConfigBannerMessage)
                protectionsBannerMessageContainer.isVisible = true
                protectionsBannerMessageContainer.setBackgroundResource(R.drawable.background_site_protections_toggle_banner_alert)
            }
        }
    }
}
