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

package com.duckduckgo.networkprotection.internal.feature

import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TestNetPInternalFeatureToggles {

    private lateinit var toggles: NetPInternalFeatureToggles

    @Before
    fun setup() {
        toggles = FeatureToggles.Builder()
            .store(FakeToggleStore())
            .appVersionProvider { Int.MAX_VALUE }
            .featureName("test")
            .build()
            .create(NetPInternalFeatureToggles::class.java)
    }

    @Test
    fun testDefaultValues() {
        assertTrue(toggles.self().isEnabled())
        assertFalse(toggles.excludeSystemApps().isEnabled())
        assertFalse(toggles.enablePcapRecording().isEnabled())
    }
}
