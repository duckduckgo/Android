/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.remoteconfig

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureExceptions.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RealAutoconsentExceptionsRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val autoconsentFeature = FakeFeatureToggleFactory.create(AutoconsentFeature::class.java)

    @Before
    fun setup() {
        autoconsentFeature.self().setRawStoredState(Toggle.State(exceptions = listOf(exception)))
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadedIntoMemory() = runTest {
        val repository = RealAutoconsentExceptionsRepository(
            TestScope(),
            coroutineRule.testDispatcherProvider,
            autoconsentFeature,
            isMainProcess = true,
        )
        assertEquals(exception, repository.exceptions.first())
    }

    @Test
    fun whenRemoteConfigUpdateThenExceptionsUpdated() = runTest {
        val repository = RealAutoconsentExceptionsRepository(
            TestScope(),
            coroutineRule.testDispatcherProvider,
            autoconsentFeature,
            isMainProcess = true,
        )

        assertEquals(listOf(exception), repository.exceptions)
        autoconsentFeature.self().setRawStoredState(Toggle.State(exceptions = emptyList()))
        repository.onPrivacyConfigDownloaded()
        assertEquals(emptyList<FeatureException>(), repository.exceptions)
    }

    companion object {
        val exception = FeatureException("example.com", "reason")
    }
}
