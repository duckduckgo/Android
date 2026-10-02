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

package com.duckduckgo.pir.impl.freemium

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PirFreemiumUpsellPluginTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val dataStore: PirFreemiumDataStore = mock()
    private val pirPixelSender: PirPixelSender = mock()

    private val testee = PirFreemiumUpsellPlugin(
        pirFreemiumDataStore = dataStore,
        pirPixelSender = pirPixelSender,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `test reports the upsell when the user activated freemium`() = runTest {
        whenever(dataStore.didActivate).thenReturn(true)

        testee.onSubscriptionPurchaseSuccess()

        verify(pirPixelSender).reportFreemiumUpsell()
    }

    @Test
    fun `test reports nothing when the user never activated freemium`() = runTest {
        whenever(dataStore.didActivate).thenReturn(false)

        testee.onSubscriptionPurchaseSuccess()

        verify(pirPixelSender, never()).reportFreemiumUpsell()
    }
}
