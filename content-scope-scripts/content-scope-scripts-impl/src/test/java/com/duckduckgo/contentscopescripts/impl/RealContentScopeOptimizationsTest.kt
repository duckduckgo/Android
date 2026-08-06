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

package com.duckduckgo.contentscopescripts.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RealContentScopeOptimizationsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val contentScopeScriptsFeature = FakeFeatureToggleFactory.create(ContentScopeScriptsFeature::class.java)

    private val testee = RealContentScopeOptimizations(
        contentScopeScriptsFeature = contentScopeScriptsFeature,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenEveryFlagIsEnabledThenEveryOptimizationIsReportedActive() = runTest {
        setFlags(injection = true, messaging = true, experiments = true)

        val state = testee.current()

        assertTrue(state.injectionOptimized)
        assertTrue(state.messagingOptimized)
        assertTrue(state.experimentsCached)
    }

    @Test
    fun whenEveryFlagIsDisabledThenNoOptimizationIsReportedActive() = runTest {
        setFlags(injection = false, messaging = false, experiments = false)

        val state = testee.current()

        assertFalse(state.injectionOptimized)
        assertFalse(state.messagingOptimized)
        assertFalse(state.experimentsCached)
    }

    @Test
    fun whenOnlyInjectionIsEnabledThenOnlyInjectionIsReportedActive() = runTest {
        setFlags(injection = true, messaging = false, experiments = false)

        val state = testee.current()

        assertTrue(state.injectionOptimized)
        assertFalse(state.messagingOptimized)
        assertFalse(state.experimentsCached)
    }

    @Test
    fun whenOnlyMessagingIsEnabledThenOnlyMessagingIsReportedActive() = runTest {
        setFlags(injection = false, messaging = true, experiments = false)

        val state = testee.current()

        assertFalse(state.injectionOptimized)
        assertTrue(state.messagingOptimized)
        assertFalse(state.experimentsCached)
    }

    @Test
    fun whenOnlyExperimentCachingIsEnabledThenOnlyExperimentCachingIsReportedActive() = runTest {
        setFlags(injection = false, messaging = false, experiments = true)

        val state = testee.current()

        assertFalse(state.injectionOptimized)
        assertFalse(state.messagingOptimized)
        assertTrue(state.experimentsCached)
    }

    @Test
    fun whenAFlagFlipsAfterAReadThenTheNextReadReportsTheNewValue() = runTest {
        setFlags(injection = false, messaging = false, experiments = false)
        assertFalse(testee.current().injectionOptimized)

        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))

        assertTrue(testee.current().injectionOptimized)
    }

    private fun setFlags(
        injection: Boolean,
        messaging: Boolean,
        experiments: Boolean,
    ) {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = injection))
        contentScopeScriptsFeature.optimizeContentScopeMessaging().setRawStoredState(State(enable = messaging))
        contentScopeScriptsFeature.cacheContentScopeExperiments().setRawStoredState(State(enable = experiments))
    }
}
