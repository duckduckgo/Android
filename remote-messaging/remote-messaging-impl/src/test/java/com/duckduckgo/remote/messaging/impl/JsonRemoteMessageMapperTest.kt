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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.app.remotemessage.impl.JsonRemoteMessage
import com.duckduckgo.app.remotemessage.impl.JsonRemoteMessageMapper
import com.duckduckgo.app.remotemessage.impl.messages.RemoteMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aBigSingleActionJsonMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aBigTwoActionJsonMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aJsonMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aMediumJsonMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aSmallJsonMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.emptyJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.mediumJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.smallJsonContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aBigSingleActionMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aBigTwoActionsMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aMediumMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aSmallMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class JsonRemoteMessageMapperTest(private val testCase: TestCase) {

    private val testee = JsonRemoteMessageMapper()

    @Test
    fun whenJsonMessageThenReturnMessage() {
        val remoteMessages = testee.map(testCase.jsonRemoteMessages)

        assertEquals(testCase.expectedMessages, remoteMessages)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters()
        fun parameters() = arrayOf(
            TestCase(
                listOf(
                    aSmallJsonMessage(id = "id1"),
                    aMediumJsonMessage(id = "id2"),
                    aBigSingleActionJsonMessage(id = "id3"),
                    aBigTwoActionJsonMessage(id = "id4")
                ),
                listOf(
                    aSmallMessage(id = "id1"),
                    aMediumMessage(id = "id2"),
                    aBigSingleActionMessage(id = "id3"),
                    aBigTwoActionsMessage(id = "id4"),
                )
            ),
            TestCase(
                listOf(
                    aSmallJsonMessage(id = "id1", content = emptyJsonContent()),
                    aMediumJsonMessage(id = "id2", content = smallJsonContent()),
                    aBigSingleActionJsonMessage(id = "id3", content = mediumJsonContent()),
                    aBigTwoActionJsonMessage(id = "id4", content = mediumJsonContent())
                ),
                emptyList()
            ),
            TestCase(
                listOf(
                    aSmallJsonMessage(id = "id1", content = emptyJsonContent()),
                    aMediumJsonMessage(id = "id2", content = emptyJsonContent()),
                    aBigSingleActionJsonMessage(id = "id3", content = emptyJsonContent()),
                    aBigTwoActionJsonMessage(id = "id4", content = emptyJsonContent())
                ),
                emptyList()
            ),
            TestCase(
                listOf(
                    aJsonMessage(id = ""),
                    aJsonMessage(messageType = ""),
                    aJsonMessage(content = null)
                ),
                emptyList()
            )
        )
    }

    data class TestCase(
        val jsonRemoteMessages: List<JsonRemoteMessage>,
        val expectedMessages: List<RemoteMessage>
    )
}