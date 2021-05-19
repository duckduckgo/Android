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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.mobile.android.vpn.R

class DeviceShieldMostRecentActivity : AppCompatActivity(R.layout.activity_device_shield_all_tracer_activity) {

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, DeviceShieldMostRecentActivity::class.java)
        }
    }
}
