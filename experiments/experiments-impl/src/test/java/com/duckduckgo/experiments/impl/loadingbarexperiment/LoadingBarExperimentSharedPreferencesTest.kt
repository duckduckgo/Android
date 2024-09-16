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

package com.duckduckgo.experiments.impl.loadingbarexperiment

import android.content.SharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever

class LoadingBarExperimentSharedPreferencesTest {

    private lateinit var testee: LoadingBarExperimentSharedPreferences

    private val sharedPreferencesProvider: SharedPreferencesProvider = mock()
    private val sharedPreferences: SharedPreferences = mock()

    @Before
    fun setUp() {
        whenever(sharedPreferencesProvider.getSharedPreferences("com.duckduckgo.app.loadingbarexperiment")).thenReturn(sharedPreferences)

        testee = LoadingBarExperimentSharedPreferences(sharedPreferencesProvider)
    }

    @Test
    fun whenVariantIsSetToTrueThenReturnTrue() {
        whenever(sharedPreferences.getBoolean("com.duckduckgo.app.loadingbarexperiment.variant", false)).thenReturn(true)

        assertTrue(testee.variant)
    }

    @Test
    fun whenVariantIsSetToFalseThenReturnFalse() {
        whenever(sharedPreferences.getBoolean("com.duckduckgo.app.loadingbarexperiment.variant", false)).thenReturn(false)

        assertFalse(testee.variant)
    }

    @Test
    fun whenVariantIsNotSetThenHasVariantReturnFalse() {
        whenever(sharedPreferences.contains("com.duckduckgo.app.loadingbarexperiment.variant")).thenReturn(false)

        assertFalse(testee.hasVariant)
    }

    @Test
    fun whenVariantIsSetThenHasVariantReturnTrue() {
        whenever(sharedPreferences.contains("com.duckduckgo.app.loadingbarexperiment.variant")).thenReturn(true)

        assertTrue(testee.hasVariant)
    }
}
