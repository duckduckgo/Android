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

package com.duckduckgo.duckchat.impl.nativeinput

import app.cash.turbine.test
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.terms.DuckAiTermsRepository
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealNewChatHintsEligibilityTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val conversions = MutableStateFlow<DuckAiBridgeSettingEntity?>(null)
    private val termsAccepted = MutableStateFlow(false)
    private val fireConversions = MutableStateFlow<DuckAiBridgeSettingEntity?>(null)
    private val fireTermsAccepted = MutableStateFlow(false)
    private val fireSettingsDao: DuckAiBridgeSettingsDao = mock {
        whenever(it.observe("origin_conversions")).thenReturn(fireConversions)
    }
    private val fireStorage: DuckAiBridgeStorage = mock {
        whenever(it.settings).thenReturn(fireSettingsDao)
    }
    private val settingsDao: DuckAiBridgeSettingsDao = mock {
        whenever(it.observe("origin_conversions")).thenReturn(conversions)
    }
    private val storage: DuckAiBridgeStorage = mock {
        whenever(it.settings).thenReturn(settingsDao)
    }
    private val storageProvider: BrowserModeDataProvider<DuckAiBridgeStorage> = mock {
        whenever(it.forMode(BrowserMode.REGULAR)).thenReturn(storage)
        whenever(it.forMode(BrowserMode.FIRE)).thenReturn(fireStorage)
    }
    private val termsRepository: DuckAiTermsRepository = mock {
        whenever(it.observeTermsAccepted(BrowserMode.REGULAR)).thenReturn(termsAccepted)
        whenever(it.observeTermsAccepted(BrowserMode.FIRE)).thenReturn(fireTermsAccepted)
    }
    private val testee = RealNewChatHintsEligibility(storageProvider, termsRepository, coroutineRule.testDispatcherProvider)

    @Test
    fun whenUserHasPromptedAndAcceptedTermsThenEligible() = runTest {
        conversions.value = row("""["duckai_prompt"]""")
        termsAccepted.value = true

        testee.observeEligible(BrowserMode.REGULAR).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenConversionsAreMissingThenNotEligible() = runTest {
        termsAccepted.value = true

        testee.observeEligible(BrowserMode.REGULAR).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserHasNotPromptedThenNotEligible() = runTest {
        conversions.value = row("""["other"]""")
        termsAccepted.value = true

        testee.observeEligible(BrowserMode.REGULAR).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTermsNotAcceptedThenNotEligibleUntilTheyAre() = runTest {
        conversions.value = row("""["duckai_prompt"]""")

        testee.observeEligible(BrowserMode.REGULAR).test {
            assertFalse(awaitItem())
            termsAccepted.value = true
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenConversionsAreMalformedThenNotEligible() = runTest {
        conversions.value = row("not json")
        termsAccepted.value = true

        testee.observeEligible(BrowserMode.REGULAR).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPromptedInRegularAndFireTermsAcceptedThenEligibleInFireMode() = runTest {
        conversions.value = row("""["duckai_prompt"]""")
        fireTermsAccepted.value = true

        testee.observeEligible(BrowserMode.FIRE).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPromptedOnlyInFireThenEligibleInFireMode() = runTest {
        fireConversions.value = row("""["duckai_prompt"]""")
        fireTermsAccepted.value = true

        testee.observeEligible(BrowserMode.FIRE).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFireTermsNotAcceptedThenNotEligibleInFireModeEvenIfAcceptedInRegular() = runTest {
        conversions.value = row("""["duckai_prompt"]""")
        termsAccepted.value = true

        testee.observeEligible(BrowserMode.FIRE).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun row(value: String) = DuckAiBridgeSettingEntity(key = "origin_conversions", value = value)
}
