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

package com.duckduckgo.credentialexchange.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealCredentialExchangeTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val credentialExchangeFeature = FakeFeatureToggleFactory.create(CredentialExchangeFeature::class.java)
    private val exporterAppDetector: ExporterAppDetector = mock()

    private val testee = RealCredentialExchange(
        exporterAppDetector = exporterAppDetector,
        credentialExchangeFeature = credentialExchangeFeature,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenNoExporterAppInstalledThenNotSupported() = runTest {
        givenFeatureEnabled(true)
        whenever(exporterAppDetector.exporterApps()) doReturn emptyList()

        assertFalse(testee.isImportSupported())
    }

    @Test
    fun whenFeatureDisabledThenNotSupportedEvenWithAnExporterInstalled() = runTest {
        givenFeatureEnabled(false)
        whenever(exporterAppDetector.exporterApps()) doReturn listOf("com.example.exporter")

        assertFalse(testee.isImportSupported())
    }

    @Test
    fun whenFeatureEnabledAndAnExporterAppIsInstalledThenSupported() = runTest {
        givenFeatureEnabled(true)
        whenever(exporterAppDetector.exporterApps()) doReturn listOf("com.example.exporter")

        assertTrue(testee.isImportSupported())
    }

    private fun givenFeatureEnabled(enabled: Boolean) {
        credentialExchangeFeature.self().setRawStoredState(State(enabled))
    }
}
