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

package com.duckduckgo.app.drm

import android.webkit.PermissionRequest
import androidx.core.net.toUri
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Test

class DrmRequestManagerTest {

    val testee: DrmRequestManager = DrmRequestManager()

    @Test
    fun whenOnPermissionRequestIfProtectedMediaIdIsRequestedThenPermissionIsReturned() {
        val permissions = arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
        val mockPermission: PermissionRequest = mock()
        whenever(mockPermission.resources).thenReturn(permissions)
        whenever(mockPermission.origin).thenReturn("https://www.spotify.com".toUri())

        val value = testee.drmPermissionsForRequest(mockPermission)

        assertEquals(1, value.size)
        assertEquals(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID, value.first())
    }

    @Test
    fun whenOnPermissionRequestIfProtectedMediaIdIsNotRequestedThenNoPermissionsAreReturned() {
        val permissions = arrayOf(PermissionRequest.RESOURCE_MIDI_SYSEX, PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        val mockPermission: PermissionRequest = mock()
        whenever(mockPermission.resources).thenReturn(permissions)
        whenever(mockPermission.origin).thenReturn("https://www.spotify.com".toUri())

        val value = testee.drmPermissionsForRequest(mockPermission)

        assertEquals(0, value.size)
    }

    @Test
    fun whenOnPermissionRequestIfDomainIsNotInListThenNoPermissionsAreReturned() {
        val permissions = arrayOf(PermissionRequest.RESOURCE_MIDI_SYSEX, PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        val mockPermission: PermissionRequest = mock()
        whenever(mockPermission.resources).thenReturn(permissions)
        whenever(mockPermission.origin).thenReturn("https://www.spotify.com".toUri())

        val value = testee.drmPermissionsForRequest(mockPermission)

        assertEquals(0, value.size)
    }

}
