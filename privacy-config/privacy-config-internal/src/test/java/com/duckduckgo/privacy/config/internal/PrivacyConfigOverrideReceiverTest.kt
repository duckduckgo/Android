/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.privacy.config.internal

import android.content.Intent
import androidx.work.WorkManager
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class PrivacyConfigOverrideReceiverTest {
    private val workManager = mock<WorkManager>()
    private val receiver = PrivacyConfigOverrideReceiver().apply { workManager = this@PrivacyConfigOverrideReceiverTest.workManager }

    @Test
    fun whenSetUrlIsInvalidThenDoNotEnqueueWork() {
        listOf(null, "", "file:///tmp/config.json", "http://", "https:///config.json").forEach { url ->
            val intent = mock<Intent>()
            whenever(intent.action).thenReturn("com.duckduckgo.privacy.config.internal.SET_URL")
            whenever(intent.getStringExtra("url")).thenReturn(url)
            receiver.onReceive(mock(), intent)
        }
        verifyNoInteractions(workManager)
    }

    @Test
    fun whenActionUnknownThenDoNotEnqueueWork() {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn("unknown")
        receiver.onReceive(mock(), intent)
        verifyNoInteractions(workManager)
    }
}
