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

package com.duckduckgo.remote.messaging.impl.systemsettings

import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.duckduckgo.remote.messaging.impl.systemsettings.SystemSettingsScreen.SAMSUNG_HOME_SCREEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemSettingsDeeplinkMapperTest {

    private val mapper = SystemSettingsActivity_SystemSettingsScreenParams_Mapper()

    @Test
    fun whenScreenIsSamsungHomeScreenThenMapsToParams() {
        val params = mapper.map(DeeplinkActivityParams("systemSettings", """{"screen": "samsungHomeScreen"}"""))

        assertEquals(SystemSettingsScreenParams(SAMSUNG_HOME_SCREEN), params)
    }

    @Test
    fun whenScreenIsUnknownThenReturnsNull() {
        assertNull(mapper.map(DeeplinkActivityParams("systemSettings", """{"screen": "pixelHomeScreen"}""")))
    }

    @Test
    fun whenPayloadIsEmptyThenReturnsNull() {
        assertNull(mapper.map(DeeplinkActivityParams("systemSettings", "")))
    }

    @Test
    fun whenScreenNameDoesNotMatchThenReturnsNull() {
        assertNull(mapper.map(DeeplinkActivityParams("settings", """{"screen": "samsungHomeScreen"}""")))
    }

    @Test
    fun whenStartedWithParamsThenResolvesTrampolineActivity() {
        assertEquals(SystemSettingsActivity::class.java, mapper.map(SystemSettingsScreenParams(SAMSUNG_HOME_SCREEN)))
    }
}
