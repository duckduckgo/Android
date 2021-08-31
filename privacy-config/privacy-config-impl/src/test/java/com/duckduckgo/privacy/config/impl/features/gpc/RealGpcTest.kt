/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl.features.gpc

import android.content.Context
import android.content.res.Resources
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.GpcException
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.R
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER_VALUE
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class RealGpcTest {
    private val mockGpcRepository: GpcRepository = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockContext: Context = mock()
    private val mockResources: Resources = mock()
    lateinit var testee: RealGpc

    @Before
    fun setup() {
        whenever(mockContext.resources).thenReturn(mockResources)
        whenever(mockResources.openRawResource(any())).thenReturn(ByteArrayInputStream("".toByteArray()))
        whenever(mockGpcRepository.exceptions).thenReturn(arrayListOf(GpcException(EXCEPTION_URL)))

        testee = RealGpc(mockContext, mockFeatureToggle, mockGpcRepository)
    }

    @Test
    fun whenGetGpcJsThenOpenRawResourceCalled() {
        testee.getGpcJs()
        verify(mockResources).openRawResource(R.raw.gpc)
    }

    @Test
    fun whenIsEnabledThenIsGpcEnabledCalled() {
        testee.isEnabled()
        verify(mockGpcRepository).isGpcEnabled()
    }

    @Test
    fun whenEnableGpcThenEnableGpcCalled() {
        testee.enableGpc()
        verify(mockGpcRepository).enableGpc()
    }

    @Test
    fun whenDisableGpcThenDisableGpcCalled() {
        testee.disableGpc()
        verify(mockGpcRepository).disableGpc()
    }

    @Test
    fun whenGetHeadersIfFeatureAndGpcAreEnabledAndUrlIsInExceptionsThenReturnEmptyMap() {
        givenFeatureAndGpcAreEnabled()

        val result = testee.getHeaders(EXCEPTION_URL)

        assertEquals(0, result.size)
    }

    @Test
    fun whenGetHeadersIfFeatureAndGpcAreEnabledAndUrlIsNotInExceptionsThenReturnMapWithHeaders() {
        givenFeatureAndGpcAreEnabled()

        val result = testee.getHeaders("test.com")

        assertTrue(result.containsKey(GPC_HEADER))
        assertEquals(GPC_HEADER_VALUE, result[GPC_HEADER])
    }

    @Test
    fun whenGetHeadersIfFeatureIsEnabledAndGpcIsNotEnabledAndUrlIsNotInExceptionsThenReturnEmptyMap() {
        givenFeatureIsEnabledButGpcIsNot()

        val result = testee.getHeaders("test.com")

        assertEquals(0, result.size)
    }

    @Test
    fun whenGetHeadersIfFeatureIsNotEnabledAndGpcIsEnabledAndUrlIsNotInExceptionsThenReturnEmptyMap() {
        givenFeatureIsNotEnabledButGpcIsEnabled()

        val result = testee.getHeaders("test.com")

        assertEquals(0, result.size)
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureAndGpcAreEnabledAndAndUrlIsInExceptionsThenReturnFalse() {
        givenFeatureAndGpcAreEnabled()

        assertFalse(testee.canUrlAddHeaders(EXCEPTION_URL, emptyMap()))
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureAndGpcAreEnabledAndAndUrlIsInConsumersListsAndHeaderAlreadyExistsThenReturnFalse() {
        givenFeatureAndGpcAreEnabled()

        assertFalse(testee.canUrlAddHeaders(VALID_CONSUMER_URL, mapOf(GPC_HEADER to GPC_HEADER_VALUE)))
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureAndGpcAreEnabledAndAndUrlIsInConsumersListAndHeaderDoNotExistsThenReturnTrue() {
        givenFeatureAndGpcAreEnabled()

        assertTrue(testee.canUrlAddHeaders(VALID_CONSUMER_URL, emptyMap()))
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureAndGpcAreEnabledAndAndUrlIsInNotInConsumersListAndHeaderDoNotExistsThenReturnFalse() {
        givenFeatureAndGpcAreEnabled()

        assertFalse(testee.canUrlAddHeaders("test.com", emptyMap()))
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureAndGpcAreEnabledAndUrlIsInConsumersButInTheExceptionListThenReturnFalse() {
        whenever(mockGpcRepository.exceptions).thenReturn(arrayListOf(GpcException(VALID_CONSUMER_URL)))
        givenFeatureAndGpcAreEnabled()

        assertFalse(testee.canUrlAddHeaders(VALID_CONSUMER_URL, emptyMap()))
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureIsNotEnabledAndGpcIsEnabledAndAndUrlIsInConsumersListAndHeaderDoNotExistsThenReturnFalse() {
        givenFeatureIsNotEnabledButGpcIsEnabled()

        assertFalse(testee.canUrlAddHeaders(VALID_CONSUMER_URL, emptyMap()))
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureIsEnabledAndGpcIsNotEnabledAndAndUrlIsInConsumersListAndHeaderDoNotExistsThenReturnFalse() {
        givenFeatureIsEnabledButGpcIsNot()

        assertFalse(testee.canUrlAddHeaders(VALID_CONSUMER_URL, emptyMap()))
    }

    @Test
    fun whenCanGpcBeUsedByUrlIfFeatureAndGpcAreEnabledAnUrlIsNotAnExceptionThenReturnTrue() {
        givenFeatureAndGpcAreEnabled()

        assertTrue(testee.canGpcBeUsedByUrl("test.com"))
    }

    @Test
    fun whenCanGpcBeUsedByUrlIfFeatureAndGpcAreEnabledAnUrlIsAnExceptionThenReturnFalse() {
        givenFeatureAndGpcAreEnabled()

        assertFalse(testee.canGpcBeUsedByUrl(EXCEPTION_URL))
    }

    @Test
    fun whenCanGpcBeUsedByUrlIfFeatureIsEnabledAndGpcIsNotEnabledAnUrlIsNotAnExceptionThenReturnFalse() {
        givenFeatureIsEnabledButGpcIsNot()

        assertFalse(testee.canGpcBeUsedByUrl("test.com"))
    }

    @Test
    fun whenCanGpcBeUsedByUrlIfFeatureIsNotEnabledAndGpcIsEnabledAnUrlIsNotAnExceptionThenReturnFalse() {
        givenFeatureIsNotEnabledButGpcIsEnabled()

        assertFalse(testee.canGpcBeUsedByUrl("test.com"))
    }

    private fun givenFeatureAndGpcAreEnabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.GpcFeatureName(), true)).thenReturn(true)
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(true)
    }

    private fun givenFeatureIsEnabledButGpcIsNot() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.GpcFeatureName(), true)).thenReturn(true)
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(false)
    }

    private fun givenFeatureIsNotEnabledButGpcIsEnabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.GpcFeatureName(), true)).thenReturn(false)
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(true)
    }

    companion object {
        const val EXCEPTION_URL = "example.com"
        const val VALID_CONSUMER_URL = "global-privacy-control.glitch.me"
    }
}
