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

import com.duckduckgo.browser.api.DeviceProperties
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aJsonMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.bigSingleActionJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.bigTwoActionJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.emptyJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.mediumJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.smallJsonContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aBigSingleActionMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aBigTwoActionsMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aMediumMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aSmallMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.bigSingleActionContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.bigTwoActionsContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.mediumContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.smallContent
import com.duckduckgo.remote.messaging.impl.mappers.JsonRemoteMessageMapper
import com.duckduckgo.remote.messaging.impl.models.JsonContentTranslations
import com.duckduckgo.remote.messaging.impl.models.JsonRemoteMessage
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*

@RunWith(Parameterized::class)
class JsonRemoteMessageMapperTest(private val testCase: TestCase) {

    private val deviceProperties: DeviceProperties = mock<DeviceProperties>().apply {
        whenever(this.deviceLocale()).thenReturn(Locale.FRANCE)
    }
    private val testee = JsonRemoteMessageMapper(deviceProperties)

    @Test
    fun whenJsonMessageThenReturnMessage() {
        val remoteMessages = testee.map(testCase.jsonRemoteMessages)

        assertEquals(testCase.expectedMessages, remoteMessages)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun parameters() = arrayOf(
            TestCase(
                listOf(
                    aJsonMessage(id = "id1", content = smallJsonContent()),
                    aJsonMessage(id = "id2", content = mediumJsonContent()),
                    aJsonMessage(id = "id3", content = bigSingleActionJsonContent()),
                    aJsonMessage(id = "id4", content = bigTwoActionJsonContent())
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
                    aJsonMessage(id = "id1", content = emptyJsonContent()),
                    aJsonMessage(id = "id2", content = smallJsonContent()),
                    aJsonMessage(id = "id3", content = mediumJsonContent()),
                    aJsonMessage(id = "id4", content = bigSingleActionJsonContent()),
                    aJsonMessage(id = "id5", content = bigTwoActionJsonContent()),
                ),
                listOf(
                    aSmallMessage(id = "id2"),
                    aMediumMessage(id = "id3"),
                    aBigSingleActionMessage(id = "id4"),
                    aBigTwoActionsMessage(id = "id5")
                )
            ),
            TestCase(
                listOf(
                    aJsonMessage(id = "id1", content = emptyJsonContent()),
                    aJsonMessage(id = "id1", content = emptyJsonContent(messageType = "small")),
                    aJsonMessage(id = "id1", content = emptyJsonContent(messageType = "medium"))
                ),
                emptyList()
            ),
            TestCase(
                listOf(
                    aJsonMessage(id = ""),
                    aJsonMessage(content = emptyJsonContent()),
                    aJsonMessage(content = null)
                ),
                emptyList()
            ),
            TestCase(
                listOf(
                    aJsonMessage(
                        id = "id1", content = smallJsonContent(),
                        translations = mapOf("fr" to frenchTranslations())
                    ),
                    aJsonMessage(
                        id = "id2", content = mediumJsonContent(),
                        translations = mapOf("fr" to frenchTranslations())
                    ),
                    aJsonMessage(
                        id = "id3", content = bigSingleActionJsonContent(),
                        translations = mapOf("fr" to frenchTranslations())
                    ),
                    aJsonMessage(
                        id = "id4", content = bigTwoActionJsonContent(),
                        translations = mapOf("fr" to frenchTranslations())
                    )
                ),
                listOf(
                    aSmallMessage(
                        id = "id1",
                        smallContent(titleText = frenchTranslations().titleText, descriptionText = frenchTranslations().descriptionText)
                    ),
                    aMediumMessage(
                        id = "id2",
                        mediumContent(
                            titleText = frenchTranslations().titleText, descriptionText = frenchTranslations().descriptionText
                        )
                    ),
                    aBigSingleActionMessage(
                        id = "id3",
                        bigSingleActionContent(
                            titleText = frenchTranslations().titleText, descriptionText = frenchTranslations().descriptionText,
                            primaryActionText = frenchTranslations().primaryActionText
                        )
                    ),
                    aBigTwoActionsMessage(
                        id = "id4",
                        bigTwoActionsContent(
                            titleText = frenchTranslations().titleText, descriptionText = frenchTranslations().descriptionText,
                            primaryActionText = frenchTranslations().primaryActionText, secondaryActionText = frenchTranslations().secondaryActionText
                        )
                    )
                )
            )
        )

        private fun frenchTranslations() = JsonContentTranslations(
            titleText = "Bonjour", descriptionText = "la description",
            primaryActionText = "action principale",
            secondaryActionText = "action secondaire"
        )
    }

    data class TestCase(
        val jsonRemoteMessages: List<JsonRemoteMessage>,
        val expectedMessages: List<RemoteMessage>
    )
}
