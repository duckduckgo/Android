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

package com.duckduckgo.privacy.config.impl.features.drm

import android.webkit.PermissionRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.DrmException
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.store.features.drm.DrmRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class RealDrmTest {

    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockDrmRepository: DrmRepository = mock()

    val testee: RealDrm = RealDrm(mockFeatureToggle, mockDrmRepository)

    @Test
    fun whenGetDrmPermissionsForRequestIfFeatureIsEnabledAndProtectedMediaIdIsRequestedThenPermissionIsReturned() {
        giveFeatureIsEnabled()
        givenUrlIsInExceptionList()

        val permissions = arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
        val url = "https://open.spotify.com"

        val value = testee.getDrmPermissionsForRequest(url, permissions)

        assertEquals(1, value.size)
        assertEquals(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID, value.first())
    }

    @Test
    fun whenGetDrmPermissionsForRequestIfFeatureIsEnabledAndProtectedMediaIdIsNotRequestedThenNoPermissionsAreReturned() {
        giveFeatureIsEnabled()
        givenUrlIsInExceptionList()

        val permissions =
            arrayOf(PermissionRequest.RESOURCE_MIDI_SYSEX, PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        val url = "https://open.spotify.com"

        val value = testee.getDrmPermissionsForRequest(url, permissions)

        assertEquals(0, value.size)
    }

    @Test
    fun whenGetDrmPermissionsForRequestIfFeatureIsEnabledAndDomainIsNotInExceptionsListThenNoPermissionsAreReturned() {
        giveFeatureIsEnabled()
        givenUrlIsNotInExceptionList()

        val permissions =
            arrayOf(PermissionRequest.RESOURCE_MIDI_SYSEX, PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        val url = "https://test.com"

        val value = testee.getDrmPermissionsForRequest(url, permissions)

        assertEquals(0, value.size)
    }

    @Test
    fun whenGetDrmPermissionsForRequestIfFeatureIsDisableThenNoPermissionsAreReturned() {
        val permissions = arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
        val url = "https://open.spotify.com"

        val value = testee.getDrmPermissionsForRequest(url, permissions)

        assertEquals(0, value.size)
    }

    private fun giveFeatureIsEnabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(eq(PrivacyFeatureName.DrmFeatureName), any())).thenReturn(true)
    }

    private fun givenUrlIsInExceptionList() {
        val exceptions = CopyOnWriteArrayList<DrmException>().apply { add(DrmException("open.spotify.com", "my reason here")) }
        whenever(mockDrmRepository.exceptions).thenReturn(exceptions)
    }

    private fun givenUrlIsNotInExceptionList() {
        whenever(mockDrmRepository.exceptions).thenReturn(CopyOnWriteArrayList<DrmException>())
    }
}
