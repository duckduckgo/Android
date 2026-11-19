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

package com.duckduckgo.app.statistics.wideevents.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WideEventEntityTypeConvertersTest {
    private val converters = WideEventEntityTypeConverters(Moshi.Builder().build())

    private val processStartPolicy =
        WideEventEntity.CleanupPolicy.OnProcessStart(
            ownerProcessName = ":vpn",
            ignoreIfIntervalTimeoutPresent = true,
            status = WideEventEntity.WideEventStatus.CANCELLED,
            metadata = mapOf("reason" to "test"),
        )

    @Test
    fun `process-start policy round-trips with its owner`() {
        val json = converters.fromCleanupPolicy(processStartPolicy)

        assertEquals(processStartPolicy, converters.toCleanupPolicy(json))
    }

    @Test
    fun `process-start policy persists its owner under the keys written by the migration`() {
        val json = JSONObject(converters.fromCleanupPolicy(processStartPolicy))

        assertEquals(":vpn", json.getString("ownerProcessName"))
        assertEquals(true, json.getBoolean("ignoreIfIntervalTimeoutPresent"))
    }
}
