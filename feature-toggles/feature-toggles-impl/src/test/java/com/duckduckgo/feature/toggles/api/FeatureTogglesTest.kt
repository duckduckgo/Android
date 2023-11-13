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

import com.duckduckgo.appbuildconfig.api.BuildFlavor
import java.lang.IllegalStateException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeatureTogglesTest {

    private lateinit var feature: TestFeature
    private lateinit var provider: FakeProvider
    private lateinit var toggleStore: FakeToggleStore

    @Before
    fun setup() {
        provider = FakeProvider()
        toggleStore = FakeToggleStore()
        feature = FeatureToggles.Builder()
            .store(toggleStore)
            .appVersionProvider { provider.version }
            .flavorNameProvider { provider.flavorName }
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
        assertFalse(feature.enabledByDefault().isEnabled())
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
        provider.version = 10
        feature.enabledByDefault().setEnabled(Toggle.State(enable = true, minSupportedVersion = 11))
        assertFalse(feature.enabledByDefault().isEnabled())
    }

    @Test
    fun whenAllowedMinVersionThenReturnDisabled() {
        provider.version = 10
        feature.enabledByDefault().setEnabled(Toggle.State(enable = true, minSupportedVersion = 9))
        assertTrue(feature.enabledByDefault().isEnabled())
    }

    @Test
    fun testInternalAlwaysEnabledAnnotation() {
        assertFalse(feature.internal().isEnabled())

        provider.flavorName = BuildFlavor.PLAY.name
        assertFalse(feature.internal().isEnabled())

        provider.flavorName = BuildFlavor.FDROID.name
        assertFalse(feature.internal().isEnabled())

        provider.flavorName = BuildFlavor.INTERNAL.name
        assertTrue(feature.internal().isEnabled())

        // enable the feature
        val enabledState = Toggle.State(
            remoteEnableState = true,
            rollout = null,
            rolloutStep = null,
        )
        feature.internal().setEnabled(enabledState)
        provider.flavorName = BuildFlavor.PLAY.name
        assertTrue(feature.internal().isEnabled())
        provider.flavorName = BuildFlavor.FDROID.name
        assertTrue(feature.internal().isEnabled())
        provider.flavorName = BuildFlavor.INTERNAL.name
        assertTrue(feature.internal().isEnabled())

        feature.internal().setEnabled(enabledState.copy(remoteEnableState = false))
        provider.flavorName = BuildFlavor.PLAY.name
        assertFalse(feature.internal().isEnabled())
        provider.flavorName = BuildFlavor.FDROID.name
        assertFalse(feature.internal().isEnabled())
        provider.flavorName = BuildFlavor.INTERNAL.name
        assertTrue(feature.internal().isEnabled())
    }

    @Test(expected = java.lang.IllegalArgumentException::class)
    fun whenMethodWithArgumentsThenThrow() {
        feature.methodWithArguments("")
    }

    @Test(expected = java.lang.IllegalArgumentException::class)
    fun whenSuspendFunctionThenThrow() = runTest {
        feature.suspendFun()
    }

    @Test(expected = IllegalArgumentException::class)
    fun whenValidFeatureAndMissingFeatureNameBuilderParameterThenThrow() {
        FeatureToggles.Builder()
            .store(FakeToggleStore())
            .appVersionProvider { provider.version }
            .build()
            .create(TestFeature::class.java)
            .self()
    }

    @Test(expected = IllegalArgumentException::class)
    fun whenValidFeatureAndMissingStoreBuilderParameterThenThrow() {
        FeatureToggles.Builder()
            .featureName("test")
            .appVersionProvider { provider.version }
            .build()
            .create(TestFeature::class.java)
            .self()
    }

    @Test
    fun whenEnabledAndInvalidOrValidRolloutThenIsEnableReturnsTrue() {
        val state = Toggle.State(
            enable = true,
            rollout = null,
            rolloutStep = null,
        )
        feature.self().setEnabled(state)
        assertTrue(feature.self().isEnabled())

        feature.self().setEnabled(state.copy(rollout = emptyList()))
        assertTrue(feature.self().isEnabled())

        feature.self().setEnabled(state.copy(rolloutStep = 2))
        assertTrue(feature.self().isEnabled())

        feature.self().setEnabled(state.copy(rollout = listOf(1.0, 2.0)))
        assertTrue(feature.self().isEnabled())

        feature.self().setEnabled(state.copy(rollout = listOf(0.5, 2.0), rolloutStep = 0))
        assertTrue(feature.self().isEnabled())

        feature.self().setEnabled(state.copy(rollout = listOf(0.5, 100.0), rolloutStep = 1))
        assertTrue(feature.self().isEnabled())
    }

    @Test
    fun whenEnabledAndValidRolloutThenReturnKeepRolloutStep() {
        val state = Toggle.State(
            enable = true,
            rollout = listOf(100.0),
            rolloutStep = null,
        )
        val expected = state.copy(remoteEnableState = state.enable)
        feature.self().setEnabled(state)
        assertTrue(feature.self().isEnabled())
        assertEquals(expected, toggleStore.get("test"))
    }

    @Test
    fun whenDisabledAndValidRolloutThenDetermineRolloutValue() {
        val state = Toggle.State(
            enable = false,
            rollout = listOf(100.0),
            rolloutStep = null,
        )
        feature.self().setEnabled(state)
        assertTrue(feature.self().isEnabled())

        val updatedState = toggleStore.get("test")
        assertEquals(1, updatedState?.rolloutStep)
        assertTrue(updatedState!!.enable)
    }

    @Test
    fun whenDisabledAndValidRolloutWithMultipleStepsThenDetermineRolloutValue() {
        val state = Toggle.State(
            enable = false,
            rollout = listOf(1.0, 10.0, 20.0, 40.0, 100.0),
            rolloutStep = null,
        )
        feature.self().setEnabled(state)
        assertTrue(feature.self().isEnabled())

        val updatedState = toggleStore.get("test")
        assertEquals(5, updatedState?.rolloutStep)
        assertTrue(updatedState!!.enable)
    }

    @Test
    fun whenDisabledWithPreviousStepsAndValidRolloutWithMultipleStepsThenDetermineRolloutValue() {
        val state = Toggle.State(
            enable = false,
            rollout = listOf(1.0, 10.0, 20.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0),
            rolloutStep = 2,
        )
        feature.self().setEnabled(state)
        assertTrue(feature.self().isEnabled())

        val updatedState = toggleStore.get("test")
        assertTrue(updatedState?.rolloutStep!! <= state.rollout!!.size && updatedState.rolloutStep!! > 2)
        assertTrue(updatedState!!.enable)
    }

    @Test
    fun whenDisabledWithValidRolloutStepsAndNotSupportedVersionThenReturnDisabled() {
        provider.version = 10
        val state = Toggle.State(
            enable = false,
            rollout = listOf(1.0, 10.0, 20.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0),
            rolloutStep = 2,
            minSupportedVersion = 11,
        )
        feature.self().setEnabled(state)

        // the feature flag is internally enabled but isEnabled() returns disabled because it doesn't meet  minSupportedVersion
        assertFalse(feature.self().isEnabled())
        val updatedState = toggleStore.get("test")!!
        assertEquals(true, updatedState.enable)
        assertNotEquals(2, updatedState.rolloutStep)
        assertEquals(state.minSupportedVersion, updatedState.minSupportedVersion)
        assertEquals(state.rollout, updatedState.rollout)
    }

    @Test
    fun whenRemoteEnableStateIsNullThenHonourLocalEnableStateAndUpdate() {
        val state = Toggle.State(enable = false)
        feature.self().setEnabled(state)

        assertFalse(feature.self().isEnabled())
        val updatedState = toggleStore.get("test")!!
        assertEquals(false, updatedState.enable)
        assertEquals(false, updatedState.remoteEnableState)
        assertNull(updatedState.rolloutStep)
        assertNull(updatedState.rollout)
    }

    @Test
    fun whenRemoteStateDisabledThenIgnoreLocalState() {
        val state = Toggle.State(
            remoteEnableState = false,
            enable = true,
        )
        feature.self().setEnabled(state)
        assertFalse(feature.self().isEnabled())
        assertEquals(state, toggleStore.get("test"))
    }

    @Test
    fun whenRemoteStateDisabledAndValidRolloutThenIgnoreRollout() {
        val state = Toggle.State(
            remoteEnableState = false,
            enable = true,
            rollout = listOf(100.0),
            rolloutStep = null,
        )
        feature.self().setEnabled(state)
        assertFalse(feature.self().isEnabled())
        assertEquals(state, toggleStore.get("test"))
    }

    @Test
    fun whenRemoteStateEnabledAndLocalStateEnabledWithValidRolloutThenIgnoreRollout() {
        val state = Toggle.State(
            remoteEnableState = true,
            enable = true,
            rollout = listOf(100.0),
            rolloutStep = null,
        )
        feature.self().setEnabled(state)
        assertTrue(feature.self().isEnabled())
        assertEquals(state, toggleStore.get("test"))
    }

    @Test
    fun whenRemoteStateEnabledAndLocalStateDisabledWithValidRolloutThenDoRollout() {
        val state = Toggle.State(
            remoteEnableState = true,
            enable = false,
            rollout = listOf(100.0),
            rolloutStep = null,
        )
        val expected = state.copy(enable = true, rollout = listOf(100.0), rolloutStep = 1)
        feature.self().setEnabled(state)
        assertTrue(feature.self().isEnabled())
        assertEquals(expected, toggleStore.get("test"))
    }

    @Test
    fun whenAppUpdateThenEvaluatePreviousState() {
        // when remoteEnableState is null it means app update
        val state = Toggle.State(
            remoteEnableState = null,
            enable = true,
        )

        // Use directly the store because setEnabled() populates the local state when the remote state is null
        toggleStore.set("test_disableByDefault", state)
        assertTrue(feature.disableByDefault().isEnabled())

        // Use directly the store because setEnabled() populates the local state when the remote state is null
        toggleStore.set("test_enabledByDefault", state.copy(enable = false))
        assertFalse(feature.enabledByDefault().isEnabled())
    }
}

interface TestFeature {
    @Toggle.DefaultValue(true)
    fun self(): Toggle

    @Toggle.DefaultValue(false)
    fun disableByDefault(): Toggle

    @Toggle.DefaultValue(true)
    fun enabledByDefault(): Toggle
    fun noDefaultValue(): Toggle

    @Toggle.DefaultValue(true)
    fun wrongReturnValue(): Boolean

    @Toggle.DefaultValue(true)
    fun methodWithArguments(arg: String)

    @Toggle.DefaultValue(true)
    suspend fun suspendFun(): Toggle

    @Toggle.DefaultValue(false)
    @Toggle.InternalAlwaysEnabled
    fun internal(): Toggle
}

private class FakeProvider {
    var version = Int.MAX_VALUE
    var flavorName = ""
}
