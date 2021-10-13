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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.runBlocking
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.GpcException
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.R
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER_VALUE
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RealGpcTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockGpcRepository: GpcRepository = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockContext: Context = mock()
    private val mockResources: Resources = mock()
    lateinit var testee: RealGpc

    @Before
    fun setup() {
        whenever(mockContext.resources).thenReturn(mockResources)
        whenever(mockResources.openRawResource(any())).thenReturn(ByteArrayInputStream("".toByteArray()))
        whenever(mockGpcRepository.exceptions).thenReturn(arrayListOf(GpcException(EXCEPTION_URL)))

        testee = RealGpc(mockContext, mockFeatureToggle, mockGpcRepository, mockUnprotectedTemporary)
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
    fun whenGetHeadersIfFeatureAndGpcAreEnabledAndUrlIsInExceptionsThenReturnEmptyMap() = coroutinesTestRule.runBlocking {
        givenFeatureAndGpcAreEnabled()

        val result = testee.getHeaders(EXCEPTION_URL)

        assertEquals(0, result.size)
    }

    @Test
    fun whenGetHeadersIfFeatureAndGpcAreEnabledAndUrlIsNotInExceptionsThenReturnMapWithHeaders() = coroutinesTestRule.runBlocking {
        givenFeatureAndGpcAreEnabled()

        val result = testee.getHeaders("test.com")

        assertTrue(result.containsKey(GPC_HEADER))
        assertEquals(GPC_HEADER_VALUE, result[GPC_HEADER])
    }

    @Test
    fun whenGetHeadersIfFeatureIsEnabledAndGpcIsNotEnabledAndUrlIsNotInExceptionsThenReturnEmptyMap() = coroutinesTestRule.runBlocking {
        givenFeatureIsEnabledButGpcIsNot()

        val result = testee.getHeaders("test.com")

        assertEquals(0, result.size)
    }

    @Test
    fun whenGetHeadersIfFeatureIsNotEnabledAndGpcIsEnabledAndUrlIsNotInExceptionsThenReturnEmptyMap() = coroutinesTestRule.runBlocking {
        givenFeatureIsNotEnabledButGpcIsEnabled()

        val result = testee.getHeaders("test.com")

        assertEquals(0, result.size)
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureAndGpcAreEnabledAndAndUrlIsInExceptionsThenReturnFalse() = coroutinesTestRule.runBlocking {
        givenFeatureAndGpcAreEnabled()

        assertFalse(testee.canUrlAddHeaders(EXCEPTION_URL, emptyMap()))
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureAndGpcAreEnabledAndAndUrlIsInConsumersListsAndHeaderAlreadyExistsThenReturnFalse() = coroutinesTestRule.runBlocking {
        givenFeatureAndGpcAreEnabled()

        assertFalse(testee.canUrlAddHeaders(VALID_CONSUMER_URL, mapOf(GPC_HEADER to GPC_HEADER_VALUE)))
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureAndGpcAreEnabledAndAndUrlIsInConsumersListAndHeaderDoNotExistsThenReturnTrue() = coroutinesTestRule.runBlocking {
        givenFeatureAndGpcAreEnabled()

        assertTrue(testee.canUrlAddHeaders(VALID_CONSUMER_URL, emptyMap()))
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureAndGpcAreEnabledAndAndUrlIsInNotInConsumersListAndHeaderDoNotExistsThenReturnFalse() = coroutinesTestRule.runBlocking {
        givenFeatureAndGpcAreEnabled()

        assertFalse(testee.canUrlAddHeaders("test.com", emptyMap()))
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureAndGpcAreEnabledAndUrlIsInConsumersButInTheExceptionListThenReturnFalse() = coroutinesTestRule.runBlocking {
        whenever(mockGpcRepository.exceptions).thenReturn(arrayListOf(GpcException(VALID_CONSUMER_URL)))
        givenFeatureAndGpcAreEnabled()

        assertFalse(testee.canUrlAddHeaders(VALID_CONSUMER_URL, emptyMap()))
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureIsNotEnabledAndGpcIsEnabledAndAndUrlIsInConsumersListAndHeaderDoNotExistsThenReturnFalse() = coroutinesTestRule.runBlocking {
        givenFeatureIsNotEnabledButGpcIsEnabled()

        assertFalse(testee.canUrlAddHeaders(VALID_CONSUMER_URL, emptyMap()))
    }

    @Test
    fun whenCanUrlAddHeadersIfFeatureIsEnabledAndGpcIsNotEnabledAndAndUrlIsInConsumersListAndHeaderDoNotExistsThenReturnFalse() = coroutinesTestRule.runBlocking {
        givenFeatureIsEnabledButGpcIsNot()

        assertFalse(testee.canUrlAddHeaders(VALID_CONSUMER_URL, emptyMap()))
    }

    @Test
    fun whenCanGpcBeUsedByUrlIfFeatureAndGpcAreEnabledAnUrlIsNotAnExceptionThenReturnTrue() = coroutinesTestRule.runBlocking {
        givenFeatureAndGpcAreEnabled()

        assertTrue(testee.canGpcBeUsedByUrl("test.com"))
    }

    @Test
    fun whenCanGpcBeUsedByUrlIfFeatureAndGpcAreEnabledAnUrlIsAnExceptionThenReturnFalse() = coroutinesTestRule.runBlocking {
        givenFeatureAndGpcAreEnabled()

        assertFalse(testee.canGpcBeUsedByUrl(EXCEPTION_URL))
    }

    @Test
    fun whenCanGpcBeUsedByUrlIfFeatureIsEnabledAndGpcIsNotEnabledAnUrlIsNotAnExceptionThenReturnFalse() = coroutinesTestRule.runBlocking {
        givenFeatureIsEnabledButGpcIsNot()

        assertFalse(testee.canGpcBeUsedByUrl("test.com"))
    }

    @Test
    fun whenCanGpcBeUsedByUrlIfFeatureIsNotEnabledAndGpcIsEnabledAnUrlIsNotAnExceptionThenReturnFalse() = coroutinesTestRule.runBlocking {
        givenFeatureIsNotEnabledButGpcIsEnabled()

        assertFalse(testee.canGpcBeUsedByUrl("test.com"))
    }

    @Test
    fun whenCanGpcBeUsedByUrlIfFeatureAndGpcAreEnabledAnUrlIsInUnprotectedTemporaryThenReturnFalse() = coroutinesTestRule.runBlocking {
        givenFeatureAndGpcAreEnabled()
        whenever(mockUnprotectedTemporary.isAnException(VALID_CONSUMER_URL)).thenReturn(true)

        assertFalse(testee.canGpcBeUsedByUrl(VALID_CONSUMER_URL))
    }

    private suspend fun givenFeatureAndGpcAreEnabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.GpcFeatureName(), true)).thenReturn(true)
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(true)
    }

    private suspend fun givenFeatureIsEnabledButGpcIsNot() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.GpcFeatureName(), true)).thenReturn(true)
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(false)
    }

    private suspend fun givenFeatureIsNotEnabledButGpcIsEnabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.GpcFeatureName(), true)).thenReturn(false)
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(true)
    }

    companion object {
        const val EXCEPTION_URL = "example.com"
        const val VALID_CONSUMER_URL = "global-privacy-control.glitch.me"
    }
}
