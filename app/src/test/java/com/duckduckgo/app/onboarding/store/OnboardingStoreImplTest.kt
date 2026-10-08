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

package com.duckduckgo.app.onboarding.store

import android.content.Context
import com.duckduckgo.app.onboarding.ui.page.configdriven.DownloadReasonSelection
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class OnboardingStoreImplTest {

    private val fakePreferences = InMemorySharedPreferences()
    private val sharedPreferencesProvider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doReturn fakePreferences
    }

    private val testee = OnboardingStoreImpl(
        context = mock<Context>(),
        onboardingBrandDesignUpdateToggles = mock<OnboardingBrandDesignUpdateToggles>(),
        sharedPreferencesProvider = sharedPreferencesProvider,
    )

    @Test
    fun `when no download reason stored then getDownloadReason returns null`() {
        assertNull(testee.getDownloadReason())
    }

    @Test
    fun `when download reason stored then getDownloadReason round-trips every value`() {
        DownloadReasonSelection.entries.forEach { reason ->
            testee.setDownloadReason(reason)

            assertEquals(reason, testee.getDownloadReason())
        }
    }

    @Test
    fun `when download reason cleared then getDownloadReason returns null`() {
        testee.setDownloadReason(DownloadReasonSelection.AI_CHAT)

        testee.setDownloadReason(null)

        assertNull(testee.getDownloadReason())
    }

    @Test
    fun `when input screen not selected then getSegmentedPathWithAiInput returns null`() {
        testee.setDownloadReason(DownloadReasonSelection.AI_CHAT)
        testee.storeInputScreenSelection(selected = false)

        assertNull(testee.getSegmentedPathWithAiInput())
    }

    @Test
    fun `when input screen selected then getSegmentedPathWithAiInput exposes only the search and ai paths`() {
        testee.storeInputScreenSelection(selected = true)

        testee.setDownloadReason(DownloadReasonSelection.SEARCH)
        assertEquals(DownloadReasonSelection.SEARCH, testee.getSegmentedPathWithAiInput())

        testee.setDownloadReason(DownloadReasonSelection.AI_CHAT)
        assertEquals(DownloadReasonSelection.AI_CHAT, testee.getSegmentedPathWithAiInput())

        testee.setDownloadReason(DownloadReasonSelection.NO_AI)
        assertNull(testee.getSegmentedPathWithAiInput())

        testee.setDownloadReason(DownloadReasonSelection.BLOCK_ADS)
        assertNull(testee.getSegmentedPathWithAiInput())
    }
}
