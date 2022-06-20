/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.voice.impl

import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PermissionAwareVoiceSearchLauncherTest {
    @Mock
    private lateinit var permissionRequest: PermissionRequest

    @Mock
    private lateinit var voiceSearchActivityLauncher: VoiceSearchActivityLauncher

    @Mock
    private lateinit var voiceSearchPermissionCheck: VoiceSearchPermissionCheck

    private lateinit var testee: PermissionAwareVoiceSearchLauncher

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = PermissionAwareVoiceSearchLauncher(permissionRequest, voiceSearchActivityLauncher, voiceSearchPermissionCheck)
    }

    @Test
    fun whenPermissionsNotGrantedThenLaunchPermissionRequest() {
        whenever(voiceSearchPermissionCheck.hasRequiredPermissionsGranted()).thenReturn(false)

        testee.launch(mock())

        verify(permissionRequest).launch(any())
        verify(voiceSearchActivityLauncher, never()).launch(any())
    }

    @Test
    fun whenPermissionsGrantedThenLaunchVoiceSearchActivity() {
        whenever(voiceSearchPermissionCheck.hasRequiredPermissionsGranted()).thenReturn(true)

        testee.launch(mock())

        verify(voiceSearchActivityLauncher).launch(any())
        verify(permissionRequest, never()).launch(any())
    }
}
