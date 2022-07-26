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
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class AllowNotificationsRule:Instrumentation(), TestRule {

    private val device: UiDevice = uiDevice

    override fun before() {
        allowNotifications()
    }

    override fun after() {
        clearNotificationPermissionValue()
    }

    private fun allowNotifications() {
        putDeviceSetting("NOTIFICATION_PERMISSION", 1)
    }

    private fun clearNotificationPermissionValue() {
        putDeviceSetting("NOTIFICATION_PERMISSION", 0)
    }

    private fun putDeviceSetting(key: String,value: Int) {
        device.executeShellCommand("settings put global $key $value")
    }

    override fun apply(
        base: Statement?,
        description: Description?
    ): Statement {
        TODO("Not yet implemented")
    }
}
