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

package com.duckduckgo.duckchat.impl.wideevents

import com.duckduckgo.duckchat.api.DuckAiSessionExitTrigger
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock

class DuckAiSessionCallbackFanOutTest {

    private val session: RealDuckAiSessionWideEvent = mock()
    private val responseWait: DuckAiResponseWaitWideEventImpl = mock()
    private val testee = DuckAiSessionCallbackFanOut(session, responseWait)

    @Test
    fun `when each callback is called then both receivers get it in order`() {
        testee.onLaunchLandingResolved("tab", "https://duck.ai")
        testee.onDuckAiPageVisible("tab", "https://duck.ai")
        testee.onSelectedTabChanged("tab", "https://duck.ai")
        testee.onExitIntent("tab", DuckAiSessionExitTrigger.BACK_OR_CLOSE)
        testee.onPromptSubmitted("tab")
        testee.onNewChatCreated("tab")

        inOrder(session, responseWait) {
            verify(session).onLaunchLandingResolved("tab", "https://duck.ai")
            verify(responseWait).onLaunchLandingResolved("tab", "https://duck.ai")
            verify(session).onDuckAiPageVisible("tab", "https://duck.ai")
            verify(responseWait).onDuckAiPageVisible("tab", "https://duck.ai")
            verify(session).onSelectedTabChanged("tab", "https://duck.ai")
            verify(responseWait).onSelectedTabChanged("tab", "https://duck.ai")
            verify(session).onExitIntent("tab", DuckAiSessionExitTrigger.BACK_OR_CLOSE)
            verify(responseWait).onExitIntent("tab", DuckAiSessionExitTrigger.BACK_OR_CLOSE)
            verify(session).onPromptSubmitted("tab")
            verify(responseWait).onPromptSubmitted("tab")
            verify(session).onNewChatCreated("tab")
            verify(responseWait).onNewChatCreated("tab")
        }
    }
}
