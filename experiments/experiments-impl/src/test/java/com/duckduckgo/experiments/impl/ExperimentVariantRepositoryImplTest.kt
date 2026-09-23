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

package com.duckduckgo.experiments.impl

import com.duckduckgo.app.statistics.store.StatisticsDataStore
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExperimentVariantRepositoryImplTest {

    private val store: StatisticsDataStore = mock()

    private lateinit var testee: ExperimentVariantRepositoryImpl

    @Before
    fun setup() {
        testee = ExperimentVariantRepositoryImpl(store)
    }

    @Test
    fun whenVariantIsDefaultThenUpdateAppReferrerVariantWritesVariantAndReferrerVariant() {
        whenever(store.variant).thenReturn("")

        testee.updateAppReferrerVariant("sg")

        verify(store).variant = "sg"
        verify(store).referrerVariant = "sg"
    }

    @Test
    fun whenVariantIsReinstallThenUpdateAppReferrerVariantStillWrites() {
        whenever(store.variant).thenReturn("ru")

        testee.updateAppReferrerVariant("sg")

        verify(store).variant = "sg"
        verify(store).referrerVariant = "sg"
    }

    @Test
    fun whenVariantIsDefaultThenUpdateVariantDoesNotWrite() {
        whenever(store.variant).thenReturn("")

        testee.updateVariant("xx")

        verify(store, never()).variant = any()
    }

    @Test
    fun whenVariantIsReinstallThenUpdateVariantDoesNotWrite() {
        whenever(store.variant).thenReturn("ru")

        testee.updateVariant("xx")

        verify(store, never()).variant = any()
    }

    @Test
    fun whenVariantIsExperimentKeyThenUpdateVariantWrites() {
        whenever(store.variant).thenReturn("ab")

        testee.updateVariant("xx")

        verify(store).variant = "xx"
    }
}
