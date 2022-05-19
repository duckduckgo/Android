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

package com.duckduckgo.rules

import android.app.Instrumentation
import androidx.test.uiautomator.UiDevice
import com.duckduckgo.utils.uiDevice

class TurnOffAnimationRule : Instrumentation() {

    private val device: UiDevice = uiDevice

     override fun before() {
        setAnimationsEnabled(false)
    }

    override fun after() {
        setAnimationsEnabled(true)
    }

    private fun setAnimationsEnabled(isEnabled: Boolean) {
        val value = if (isEnabled) 1 else 0
        putDeviceSetting("transition_animation_scale", value)
        putDeviceSetting("window_animation_scale", value)
        putDeviceSetting("animator_duration_scale", value)
    }

    private fun putDeviceSetting(key: String, value: Int) {
        device.executeShellCommand("settings put global $key $value")
    }

}
