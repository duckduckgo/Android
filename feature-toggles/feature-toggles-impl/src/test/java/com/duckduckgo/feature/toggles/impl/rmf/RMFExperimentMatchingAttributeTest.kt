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
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class RMFExperimentMatchingAttributeTest {

    private lateinit var matcher: RMFExperimentMatchingAttributePlugin

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var inventory: FeatureTogglesInventory
    private lateinit var testFeature: TestFeature

    @Before
    fun setup() {
        testFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "rmfExperimentTestFeature",
        ).build().create(TestFeature::class.java)

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

        matcher = RMFExperimentMatchingAttributePlugin(inventory)
    }

    @Test
    fun `map - when unknown key then return null`() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf("rmfExperimentTestFeature"))

        val result = matcher.map("unknown key", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun `map - when empty value then return null`() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = emptyList<String>())

        val result = matcher.map("isUserInAnyActiveExperiment", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun `map - when unknown value type then return null`() {
        val jsonMatchingAttribute = JsonMatchingAttribute(value = 5)

        val result = matcher.map("isUserInAnyActiveExperiment", jsonMatchingAttribute)

        assertNull(result)
    }

    @Test
    fun `map - when key and value type expected then return the attribute`() {
        val toggles = listOf("toggle", "toggle2")
        val jsonMatchingAttribute = JsonMatchingAttribute(value = toggles)

        val result = matcher.map("isUserInAnyActiveExperiment", jsonMatchingAttribute)

        assertEquals(ExperimentMatchingAttribute(toggles), result)
    }

    @Test
    fun `evaluate - when no matching flags, return false`() = runTest {
        testFeature.self().setCohort()
        testFeature.subFeature1().setCohort()
        val attribute = ExperimentMatchingAttribute(listOf("missingFeature"))

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun `evaluate - when matching flags but none enabled, return false`() = runTest {
        testFeature.self().setRawStoredState(State(false))
        testFeature.subFeature1().setRawStoredState(State(false))
        val attribute = ExperimentMatchingAttribute(listOf("rmfExperimentTestFeature", "subFeature1"))

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun `evaluate - when matching flags, all are enabled but none are active experiments, return false`() = runTest {
        testFeature.self().setRawStoredState(State(true))
        testFeature.subFeature1().setRawStoredState(State(true))
        val attribute = ExperimentMatchingAttribute(listOf("rmfExperimentTestFeature", "subFeature1"))

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun `evaluate - when matching flags, and user is enrolled but experiments are not active, return false`() = runTest {
        testFeature.self().setCohort(isActive = false)
        testFeature.subFeature1().setCohort(isActive = false)
        val attribute = ExperimentMatchingAttribute(listOf("rmfExperimentTestFeature", "subFeature1"))

        val result = matcher.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun `evaluate - when matching flags and all are active experiments, return true`() = runTest {
        testFeature.self().setCohort()
        testFeature.subFeature1().setCohort()
        val attribute = ExperimentMatchingAttribute(listOf("rmfExperimentTestFeature", "subFeature1"))

        val result = matcher.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun `evaluate - when matching flags and some are active experiments, return true`() = runTest {
        testFeature.self().setRawStoredState(State(true))
        testFeature.subFeature1().setCohort()
        val attribute = ExperimentMatchingAttribute(listOf("rmfExperimentTestFeature", "subFeature1"))

        val result = matcher.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun `evaluate - when only a subset of flags found but they are active experiments, then return true`() = runTest {
        testFeature.self().setCohort()
        testFeature.subFeature1().setRawStoredState(State(true))
        val attribute = ExperimentMatchingAttribute(listOf("rmfExperimentTestFeature", "missingFlag"))

        val result = matcher.evaluate(attribute)

        assertTrue(result!!)
    }

    private suspend fun Toggle.setCohort(isActive: Boolean = true) {
        val zdt = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        setRawStoredState(
            State(
                remoteEnableState = isActive,
                enable = isActive,
                cohorts = listOf(State.Cohort(name = "control", weight = 1, enrollmentDateET = zdt)),
            ),
        )
        enroll()
    }

    abstract class TriggerTestScope private constructor()

    @ContributesRemoteFeature(
        scope = TriggerTestScope::class,
        featureName = "rmfExperimentTestFeature",
    )
    interface TestFeature {
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
