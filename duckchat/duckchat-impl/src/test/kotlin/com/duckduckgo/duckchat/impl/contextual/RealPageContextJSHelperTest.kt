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

package com.duckduckgo.duckchat.impl.contextual

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealPageContextJSHelperTest {

    private val testee = RealPageContextJSHelper()

    @Test
    fun whenNonCollectionResultMethodThenReturnsNull() = runTest {
        val result =
            testee.processPageContext(
                featureName = "pageContext",
                method = "otherMethod",
                data = JSONObject("""{"serializedPageData":"ignored"}"""),
                tabId = "tab-1",
            )

        assertNull(result)
    }

    @Test
    fun whenCollectionResultMissingDataThenReturnsNull() = runTest {
        val result =
            testee.processPageContext(
                featureName = "pageContext",
                method = "collectionResult",
                data = JSONObject("""{"foo":"bar"}"""),
                tabId = "tab-1",
            )

        assertNull(result)
    }

    @Test
    fun whenCollectionResultHasSerializedPageDataThenReturnsValue() = runTest {
        val serialized = """{"title":"Example"}"""

        val result =
            testee.processPageContext(
                featureName = "pageContext",
                method = "collectionResult",
                data = JSONObject().apply { put("serializedPageData", serialized) },
                tabId = "tab-1",
            )

        assertEquals(serialized, result)
    }

    @Test
    fun whenOnContextualOpenedThenReturnsCollectSubscription() {
        val result = testee.onContextualOpened()

        assertEquals(RealPageContextJSHelper.PAGE_CONTEXT_FEATURE_NAME, result.featureName)
        assertEquals("collect", result.subscriptionName)
    }
}
