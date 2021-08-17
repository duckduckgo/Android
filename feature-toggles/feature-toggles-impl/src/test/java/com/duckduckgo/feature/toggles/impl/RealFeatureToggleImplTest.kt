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

package com.duckduckgo.feature.toggles.impl

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.FeatureName
import com.duckduckgo.feature.toggles.api.FeatureTogglesPlugin
import org.junit.Assert.*
import org.junit.Test

class RealFeatureToggleImplTest {

    private val testee: RealFeatureToggleImpl = RealFeatureToggleImpl(FakeFeatureTogglePluginPoint())

    @Test
    fun whenFeatureNameCanBeHandledByPluginThenReturnTheCorrectValue() {
        val result = testee.isFeatureEnabled(TrueFeatureName(), false)
        assertNotNull(result)
        assertFalse(result!!) // TODO change
    }

    @Test
    fun whenFeatureNameCannotBeHandledByAnyPluginThenReturnNull() {
        val result = testee.isFeatureEnabled(NullFeatureName(), false)
        assertNull(result)
    }

    class FakeTruePlugin : FeatureTogglesPlugin {
        override fun isEnabled(featureName: FeatureName, defaultValue: Boolean): Boolean? {
            return if (featureName is TrueFeatureName) {
                true
            } else {
                null
            }
        }
    }

    class FakeFeatureTogglePluginPoint : PluginPoint<FeatureTogglesPlugin> {
        override fun getPlugins(): Collection<FeatureTogglesPlugin> {
            return listOf(FakeTruePlugin())
        }
    }

    data class TrueFeatureName(override val value: String = "true") : FeatureName
    data class NullFeatureName(override val value: String = "null") : FeatureName
}
