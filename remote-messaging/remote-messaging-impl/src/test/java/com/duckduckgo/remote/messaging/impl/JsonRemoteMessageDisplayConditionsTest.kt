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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.remote.messaging.api.MessageTrigger
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aJsonMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.smallJsonContent
import com.duckduckgo.remote.messaging.fixtures.messageActionPlugins
import com.duckduckgo.remote.messaging.impl.mappers.mapToRemoteMessage
import com.duckduckgo.remote.messaging.impl.models.JsonDisplayConditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class JsonRemoteMessageDisplayConditionsTest {

    @Test
    fun whenDisplayConditionsHasKnownTriggerThenParsed() {
        val mapped = listOf(
            aJsonMessage(
                id = "id",
                content = smallJsonContent(),
                displayConditions = JsonDisplayConditions(trigger = "after_idle", dismissAfterDaysShown = 5),
            ),
        ).mapToRemoteMessage(Locale.US, messageActionPlugins)

        val conditions = mapped.single().displayConditions
        assertEquals(MessageTrigger.AFTER_IDLE, conditions?.trigger)
        assertEquals(5, conditions?.dismissAfterDaysShown)
    }

    @Test
    fun whenDisplayConditionsHasOnlyExpiryThenTriggerIsNull() {
        val mapped = listOf(
            aJsonMessage(
                id = "id",
                content = smallJsonContent(),
                displayConditions = JsonDisplayConditions(dismissAfterDaysShown = 5),
            ),
        ).mapToRemoteMessage(Locale.US, messageActionPlugins)

        val conditions = mapped.single().displayConditions
        assertNull(conditions?.trigger)
        assertEquals(5, conditions?.dismissAfterDaysShown)
    }

    @Test
    fun whenTriggerUnrecognizedThenMessageDropped() {
        val mapped = listOf(
            aJsonMessage(
                id = "id",
                content = smallJsonContent(),
                displayConditions = JsonDisplayConditions(trigger = "some_future_trigger"),
            ),
        ).mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertTrue(mapped.isEmpty())
    }

    @Test
    fun whenNoDisplayConditionsThenNull() {
        val mapped = listOf(
            aJsonMessage(id = "id", content = smallJsonContent()),
        ).mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertNull(mapped.single().displayConditions)
    }
}
