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

package com.duckduckgo.subscriptions.impl.store

import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionOnboardingStepStoreTest {

    private val store = SubscriptionOnboardingStepStore(FakeSharedPreferencesProvider())

    @Test
    fun whenNothingCompletedThenStoreIsEmpty() {
        assertTrue(store.completedSteps().isEmpty())
        assertFalse(store.isCompleted("vpn"))
    }

    @Test
    fun whenStepMarkedCompletedThenItIsRecorded() {
        store.setCompleted("vpn")

        assertTrue(store.isCompleted("vpn"))
        assertFalse(store.isCompleted("welcome"))
        assertEquals(setOf("vpn"), store.completedSteps())
    }

    @Test
    fun whenMultipleStepsCompletedThenAllRecorded() {
        store.setCompleted("welcome")
        store.setCompleted("vpn")

        assertEquals(setOf("welcome", "vpn"), store.completedSteps())
    }
}
