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

package com.duckduckgo.eventhub.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.eventhub.impl.pixels.EventHubPixelManager
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EventHubLifecycleObserverTest {

    private val pixelManager: EventHubPixelManager = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private val observer = EventHubLifecycleObserver(
        pixelManager = pixelManager,
    )

    @Test
    fun `onStart calls onAppForegrounded`() {
        observer.onStart(lifecycleOwner)

        verify(pixelManager).onAppForegrounded()
    }

    @Test
    fun `onStop calls onAppBackgrounded`() {
        observer.onStop(lifecycleOwner)

        verify(pixelManager).onAppBackgrounded()
    }
}
