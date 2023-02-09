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

package com.duckduckgo.feature.toggles.api

import java.lang.IllegalStateException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeatureTogglesTest {

    private lateinit var feature: TestFeature
    private lateinit var versionProvider: FakeAppVersionProvider

    @Before
    fun setup() {
        versionProvider = FakeAppVersionProvider()
        feature = FeatureToggles.Builder()
            .store(FakeToggleStore())
            .appVersionProvider { versionProvider.version }
            .featureName("test")
            .build()
            .create(TestFeature::class.java)
    }

    @Test
    fun whenDisableByDefaultThenReturnDisabled() {
        assertFalse(feature.disableByDefault().isEnabled())
    }

    @Test
    fun whenDisableByDefaultAndSetEnabledThenReturnEnabled() {
        feature.disableByDefault().setEnabled(Toggle.State(enable = true))
        assertTrue(feature.disableByDefault().isEnabled())
    }

    @Test
    fun whenEnabledByDefaultThenReturnEnabled() {
        assertTrue(feature.enabledByDefault().isEnabled())
    }

    @Test
    fun whenEnabledByDefaultAndSetDisabledThenReturnDisabled() {
        feature.enabledByDefault().setEnabled(Toggle.State(enable = false))
        assertFalse(feature.disableByDefault().isEnabled())
    }

    @Test(expected = IllegalStateException::class)
    fun whenNoDefaultValueThenThrow() {
        feature.noDefaultValue().isEnabled()
    }

    @Test(expected = IllegalArgumentException::class)
    fun whenWrongReturnValueThenThrow() {
        feature.wrongReturnValue()
    }

    @Test
    fun whenNotAllowedMinVersionThenReturnDisabled() {
        versionProvider.version = 10
        feature.enabledByDefault().setEnabled(Toggle.State(enable = true, minSupportedVersion = 11))
        assertFalse(feature.enabledByDefault().isEnabled())
    }

    @Test
    fun whenAllowedMinVersionThenReturnDisabled() {
        versionProvider.version = 10
        feature.enabledByDefault().setEnabled(Toggle.State(enable = true, minSupportedVersion = 9))
        assertTrue(feature.enabledByDefault().isEnabled())
    }

    @Test(expected = java.lang.IllegalArgumentException::class)
    fun whenMethodWithArgumentsThenThrow() {
        feature.methodWithArguments("")
    }

    @Test(expected = java.lang.IllegalArgumentException::class)
    fun whenSuspendFunctionThenThrow() = runTest {
        feature.suspendFun()
    }

    @Test(expected = NullPointerException::class)
    fun wheInvalidFeatureClassThenThrow() {
        FeatureToggles.Builder()
            .store(FakeToggleStore())
            .appVersionProvider { versionProvider.version }
            .build()
            .create(InvalidTestFeature::class.java)
            .self()
    }
}

// It's invalid because it's not annotated with FeatureName to indicate global feature
interface InvalidTestFeature {
    @Toggle.DefaultValue(true)
    fun self(): Toggle
}

interface TestFeature {
    @Toggle.DefaultValue(true)
    fun self(): Toggle

    @Toggle.DefaultValue(false)
    fun disableByDefault(): Toggle

    @Toggle.DefaultValue(true)
    fun enabledByDefault(): Toggle
    fun noDefaultValue(): Toggle
    fun wrongReturnValue(): Boolean

    @Toggle.DefaultValue(true)
    fun methodWithArguments(arg: String)

    @Toggle.DefaultValue(true)
    suspend fun suspendFun(): Toggle
}

private class FakeAppVersionProvider {
    var version = Int.MAX_VALUE
}
