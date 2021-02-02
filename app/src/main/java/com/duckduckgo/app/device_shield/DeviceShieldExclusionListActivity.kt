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

package com.duckduckgo.app.device_shield

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.device_shield.ui.DeviceShieldExcludedAppView
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.vpn.exclusions.DeviceShieldExcludedApps
import kotlinx.android.synthetic.main.activity_device_shield_exclusion_list.*
import kotlinx.android.synthetic.main.include_toolbar.*
import javax.inject.Inject

class DeviceShieldExclusionListActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var deviceShieldExcludedApps: DeviceShieldExcludedApps

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_shield_exclusion_list)
        setupToolbar(toolbar)
        setupExclusionList()
    }

    private fun setupExclusionList() {
        deviceShieldExcludedApps.getExcludedApps().asSequence()
            .map {
                DeviceShieldExcludedAppView(this).apply {
                    appName = it.name
                    appType = it.type
                    appIcon = it.icon
                    isShieldEnabled = it.isExcluded
                    tag = it.packageName
                    shieldListener = object : DeviceShieldExcludedAppView.ShieldListener {
                        override fun onAppShieldChanged(view: View, enabled: Boolean) {
                            if (enabled) {
                                deviceShieldExcludedApps.addToExclusionList((view.tag as String?).orEmpty())
                            } else {
                                deviceShieldExcludedApps.removeFromExclusionList((view.tag as String?).orEmpty())
                            }
                        }
                    }
                }
            }.forEach { appView -> deviceShieldExclusionAppListContainer.addView(appView) }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, DeviceShieldExclusionListActivity::class.java)
        }
    }
}