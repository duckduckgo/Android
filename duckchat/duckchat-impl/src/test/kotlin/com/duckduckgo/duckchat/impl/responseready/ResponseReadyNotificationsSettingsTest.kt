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
package com.duckduckgo.duckchat.impl.responseready

import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class ResponseReadyNotificationsSettingsTest {

    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)

    private fun settings(json: String?) {
        feature.responseReadyNotifications().setRawStoredState(State(settings = json))
    }

    @Test
    fun `when no settings then defaults`() {
        settings(null)

        assertEquals(ResponseReadyNotificationsSettings(4.seconds, 180.seconds, 5.seconds), feature.responseReadySettings())
    }

    @Test
    fun `when settings set then values read`() {
        settings("""{"pollIntervalSeconds":2,"timeoutSeconds":60,"errorSettleSeconds":8}""")

        assertEquals(ResponseReadyNotificationsSettings(2.seconds, 60.seconds, 8.seconds), feature.responseReadySettings())
    }

    @Test
    fun `when one key missing then only that one defaults`() {
        settings("""{"pollIntervalSeconds":2,"errorSettleSeconds":8}""")

        assertEquals(ResponseReadyNotificationsSettings(2.seconds, 180.seconds, 8.seconds), feature.responseReadySettings())
    }

    @Test
    fun `when settings malformed then defaults`() {
        settings("not-json")

        assertEquals(ResponseReadyNotificationsSettings(4.seconds, 180.seconds, 5.seconds), feature.responseReadySettings())
    }

    @Test
    fun `when value is zero or negative then default`() {
        settings("""{"pollIntervalSeconds":0,"timeoutSeconds":-5,"errorSettleSeconds":8}""")

        assertEquals(ResponseReadyNotificationsSettings(4.seconds, 180.seconds, 8.seconds), feature.responseReadySettings())
    }

    @Test
    fun `when flag not set remotely then disabled`() {
        assertFalse(feature.responseReadyNotifications().isEnabled())
    }
}
