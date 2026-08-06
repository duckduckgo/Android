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

package com.duckduckgo.featuretoggles.internal.testseeder

import android.annotation.SuppressLint
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi") // setRawStoredState used to arrange test state
class FeatureFlagSeederPluginTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var feature: TestFeature
    private lateinit var plugin: FeatureFlagSeederPlugin

    interface TestFeature {
        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun self(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
        fun fooFeature(): Toggle

        @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
        fun onByDefaultFeature(): Toggle
    }

    private class FakeInventory(private val toggles: List<Toggle>) : FeatureTogglesInventory {
        override suspend fun getAll(): List<Toggle> = toggles
    }

    @Before
    fun setup() {
        feature = FeatureToggles.Builder()
            .store(FakeToggleStore())
            .appVersionProvider { Int.MAX_VALUE }
            .flavorNameProvider { "internal" }
            .featureName("testFeature")
            .ioDispatcher(coroutineTestRule.testDispatcher)
            .build()
            .create(TestFeature::class.java)
        plugin = FeatureFlagSeederPlugin(
            FakeInventory(listOf(feature.self(), feature.fooFeature(), feature.onByDefaultFeature())),
        )
    }

    @Test
    fun `sub-feature assignment forces the toggle on`() = runTest {
        plugin.apply("featureFlags", "testFeature.fooFeature=true")
        assertTrue(feature.fooFeature().isEnabled())
    }

    @Test
    fun `sub-feature assignment forces the toggle off`() = runTest {
        feature.fooFeature().setRawStoredState(Toggle.State(enable = true))
        plugin.apply("featureFlags", "testFeature.fooFeature=false")
        assertFalse(feature.fooFeature().isEnabled())
    }

    @Test
    fun `parent-only address targets the self toggle`() = runTest {
        plugin.apply("featureFlags", "testFeature=true")
        assertTrue(feature.self().isEnabled())
    }

    @Test
    fun `multiple semicolon-separated assignments all apply`() = runTest {
        plugin.apply("featureFlags", "testFeature=true;testFeature.fooFeature=true")
        assertTrue(feature.self().isEnabled())
        assertTrue(feature.fooFeature().isEnabled())
    }

    @Test
    fun `invert flips a default-off toggle on`() = runTest {
        plugin.apply("featureFlags", "testFeature.fooFeature=invert")
        assertTrue(feature.fooFeature().isEnabled())
    }

    @Test
    fun `invert flips a default-on toggle off`() = runTest {
        plugin.apply("featureFlags", "testFeature.onByDefaultFeature=invert")
        assertFalse(feature.onByDefaultFeature().isEnabled())
    }

    @Test
    fun `invert throws when the toggle already has stored state`() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                feature.fooFeature().setRawStoredState(Toggle.State(enable = true))
                plugin.apply("featureFlags", "testFeature.fooFeature=invert")
            }
        }
    }

    @Test
    fun `invert leaves the toggle untouched when it throws`() {
        feature.fooFeature().setRawStoredState(Toggle.State(enable = true))
        runCatching { runBlocking { plugin.apply("featureFlags", "testFeature.fooFeature=invert") } }
        assertTrue(feature.fooFeature().isEnabled())
    }

    @Test
    fun `invert composes with explicit assignments in one list`() = runTest {
        plugin.apply("featureFlags", "testFeature=true;testFeature.fooFeature=invert")
        assertTrue(feature.self().isEnabled())
        assertTrue(feature.fooFeature().isEnabled())
    }

    @Test
    fun `blank value is a no-op`() = runTest {
        plugin.apply("featureFlags", "")
        assertFalse(feature.fooFeature().isEnabled())
    }

    @Test
    fun `unknown flag name throws`() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { plugin.apply("featureFlags", "notAFeature=true") }
        }
    }

    @Test
    fun `malformed assignment throws`() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { plugin.apply("featureFlags", "testFeature.fooFeature") }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { plugin.apply("featureFlags", "testFeature.fooFeature=yes") }
        }
    }
}
