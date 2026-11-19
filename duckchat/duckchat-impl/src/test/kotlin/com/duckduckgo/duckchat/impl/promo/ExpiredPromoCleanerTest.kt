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

package com.duckduckgo.duckchat.impl.promo

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.promo.data.PromoCampaignRepository
import com.duckduckgo.duckchat.impl.promo.data.PromoRecordStorageException
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

class ExpiredPromoCleanerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val mockPromoCampaignRepository: PromoCampaignRepository = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private val testee = ExpiredPromoCleaner(
        duckChatFeature = duckChatFeature,
        promoCampaignRepository = mockPromoCampaignRepository,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    )

    @Test
    fun `when app comes to the foreground, then an expired promo is ended`() = runTest {
        duckChatFeature.duckAiPromoCampaigns().setRawStoredState(Toggle.State(enable = true))

        testee.onStart(lifecycleOwner)

        verify(mockPromoCampaignRepository).endPromo(checkExpiry = true)
    }

    @Test
    fun `when app comes to the foreground while promo campaigns disabled, then the record is untouched`() = runTest {
        duckChatFeature.duckAiPromoCampaigns().setRawStoredState(Toggle.State(enable = false))

        testee.onStart(lifecycleOwner)

        verify(mockPromoCampaignRepository, never()).endPromo(any())
    }

    @Test
    fun `when the record can't be written, then the failure is contained`() = runTest {
        duckChatFeature.duckAiPromoCampaigns().setRawStoredState(Toggle.State(enable = true))
        whenever(mockPromoCampaignRepository.endPromo(any())).thenAnswer {
            throw PromoRecordStorageException(IOException())
        }

        testee.onStart(lifecycleOwner)

        verify(mockPromoCampaignRepository).endPromo(checkExpiry = true)
    }
}
