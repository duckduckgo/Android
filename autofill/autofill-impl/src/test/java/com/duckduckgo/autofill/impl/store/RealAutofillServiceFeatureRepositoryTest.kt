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

package com.duckduckgo.autofill.impl.store

import android.annotation.SuppressLint
import com.duckduckgo.autofill.impl.service.AutofillServiceFeature
import com.duckduckgo.autofill.impl.service.store.RealAutofillServiceFeatureRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi") // setRawStoredState
class RealAutofillServiceFeatureRepositoryTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val autofillServiceFeature = FakeFeatureToggleFactory.create(AutofillServiceFeature::class.java)

    @Before
    fun setup() {
        autofillServiceFeature.self().setRawStoredState(Toggle.State(exceptions = listOf(exception)))
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadIntoMemory() = runTest {
        val repository = RealAutofillServiceFeatureRepository(
            true,
            "processName",
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
            autofillServiceFeature,
        )

        assertEquals(listOf(exception.domain), repository.exceptions)
    }

    @Test
    fun whenRemoteConfigUpdateThenExceptionsUpdated() = runTest {
        val repository = RealAutofillServiceFeatureRepository(
            true,
            "processName",
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
            autofillServiceFeature,
        )

        assertEquals(listOf(exception.domain), repository.exceptions)
        autofillServiceFeature.self().setRawStoredState(Toggle.State(exceptions = emptyList()))
        repository.onPrivacyConfigDownloaded()
        assertEquals(emptyList<FeatureException>(), repository.exceptions)
    }

    companion object {
        val exception = FeatureException("example.com", "reason")
    }
}
