/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.feature.toggles.impl.rmf

import android.annotation.SuppressLint
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.impl.RealFeatureTogglesInventory
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@SuppressLint("DenyListedApi")
class RMFToggleMatchingAttributeTest {

    private lateinit var matcher: RMFToggleMatchingAttributePlugin

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var inventory: FeatureTogglesInventory
    private lateinit var testFeature: RMFToggleTestFeature

    @Before
    fun setup() {
        testFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "rmfToggleTestFeature",
        ).build().create(RMFToggleTestFeature::class.java)

        inventory = RealFeatureTogglesInventory(
            setOf(
                FakeFeatureTogglesInventory(
                    features = listOf(
                        testFeature.self(),
                        testFeature.subFeature1(),
                    ),
                ),
            ),
            coroutineRule.testDispatcherProvider,
        )

        matcher = RMFToggleMatchingAttributePlugin(inventory)
    }

    @Test
    fun `map - when unknown key then return null`() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf("rmfToggleTestFeature"))

        val result = matcher.map("unknown key", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun `map - when empty value then return null`() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = emptyList<String>())

        val result = matcher.map("allFeatureFlagsEnabled", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun `map - when unknown value type then return null`() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = 5)

        val result = matcher.map("allFeatureFlagsEnabled", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun `map - when key and value type expected then return the attribute`() {
        val toggles = listOf("toggle", "toggle2")
        val jsonMatchingAttribute = JsonMatchingAttribute(value = toggles)

        val result = matcher.map("allFeatureFlagsEnabled", jsonMatchingAttribute)

        assertEquals(ToggleMatchingAttribute(toggles), result)
    }

    @Test
    fun `evaluate - when no matching flags, return false`() = runTest {
        testFeature.self().setRawStoredState(State(true))
        testFeature.subFeature1().setRawStoredState(State(true))
        val attribute = ToggleMatchingAttribute(listOf("missingFeature"))

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun `evaluate - when matching flags but none enabled, return false`() = runTest {
        testFeature.self().setRawStoredState(State(false))
        testFeature.subFeature1().setRawStoredState(State(false))
        val attribute = ToggleMatchingAttribute(listOf("rmfToggleTestFeature", "subFeature1"))

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun `evaluate - when matching flags but not all enabled, return false`() = runTest {
        testFeature.self().setRawStoredState(State(true))
        testFeature.subFeature1().setRawStoredState(State(false))
        val attribute = ToggleMatchingAttribute(listOf("rmfToggleTestFeature", "subFeature1"))

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun `evaluate - when matching flags and all enabled, return true`() = runTest {
        testFeature.self().setRawStoredState(State(true))
        testFeature.subFeature1().setRawStoredState(State(true))
        val attribute = ToggleMatchingAttribute(listOf("rmfToggleTestFeature", "subFeature1"))

        val result = matcher.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun `evaluate - when matching flags and some part of the active experiment, return true`() = runTest {
        testFeature.self().setRawStoredState(State(true))
        testFeature.subFeature1().setActiveCohort()
        val attribute = ToggleMatchingAttribute(listOf("rmfToggleTestFeature", "subFeature1"))

        val result = matcher.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun `evaluate - when only a subset of flags found, then return false`() = runTest {
        testFeature.self().setRawStoredState(State(true))
        testFeature.subFeature1().setRawStoredState(State(true))
        val attribute = ToggleMatchingAttribute(listOf("rmfToggleTestFeature", "missingFlag"))

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    private fun Toggle.setActiveCohort() {
        val zdt = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = zdt),
            ),
        )
    }

    abstract class TriggerTestScope private constructor()

    @ContributesRemoteFeature(
        scope = TriggerTestScope::class,
        featureName = "rmfToggleTestFeature",
    )
    interface RMFToggleTestFeature {
        @DefaultValue(DefaultFeatureValue.FALSE)
        fun self(): Toggle

        @DefaultValue(DefaultFeatureValue.FALSE)
        fun subFeature1(): Toggle
    }

    class FakeFeatureTogglesInventory(private val features: List<Toggle>) : FeatureTogglesInventory {
        override suspend fun getAll(): List<Toggle> {
            return features
        }
    }
}
