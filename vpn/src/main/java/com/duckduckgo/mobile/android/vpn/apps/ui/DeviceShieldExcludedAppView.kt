/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.apps.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.duckduckgo.mobile.android.vpn.BuildConfig
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import kotlinx.android.synthetic.main.view_device_shield_excluded_app_entry.view.*

class DeviceShieldExcludedAppView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val root: View by lazy {
        LayoutInflater.from(context).inflate(R.layout.view_device_shield_excluded_app_entry, this, true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        root.deviceShieldAppEntryShieldEnabled.setOnCheckedChangeListener { _, enabled ->
            shieldListener?.onAppShieldChanged(this, enabled)
        }
        // only allow to remove/add to the exclusion list in DEBUG mode
        root.deviceShieldAppEntryShieldEnabled.isVisible = BuildConfig.DEBUG
        root.deviceShieldAppEntryShieldEnabled.isEnabled = !TrackerBlockingVpnService.isServiceRunning(context)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        root.deviceShieldAppEntryShieldEnabled.setOnCheckedChangeListener(null)
    }

    var shieldListener: ShieldListener? = null

    var appName: String
        get() = root.deviceShieldAppEntryName.toString()
        set(value) { root.deviceShieldAppEntryName.text = value }

    var appType: String?
        get() = root.deviceShieldAppEntryType.toString()
        set(value) {
            root.deviceShieldAppEntryType.text = value
            root.deviceShieldAppEntryType.visibility = if (value == null) GONE else VISIBLE
        }

    var appIcon: Drawable?
        get() = root.deviceShieldAppEntryIcon.drawable
        set(value) { root.deviceShieldAppEntryIcon.setImageDrawable(value) }

    var isShieldEnabled: Boolean
        get() = root.deviceShieldAppEntryShieldEnabled.isChecked
        set(value) { root.deviceShieldAppEntryShieldEnabled.isChecked = value }

    interface ShieldListener {
        fun onAppShieldChanged(view: View, enabled: Boolean)
    }
}
