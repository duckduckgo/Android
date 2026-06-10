/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import org.junit.Assert.assertEquals
import org.junit.Test

class TdsTest {

    /**
     * If this test fails is because you updated the internal tds json file without changing the default etag value in TrackerDataLoader.
     * Change the DEFAULT_ETAG value with the latest etag header from tds and then change the value of DEFAULT_TDS_HASH_VALUE below with the new one
     * to make the test pass.
     **/

    @Test
    fun whenInternalTdsFileChangesThenEtagValueChanges() {
        val tdsContent = InstrumentationRegistry.getInstrumentation().targetContext.resources.openRawResource(R.raw.tds)
            .bufferedReader().use { it.readText() }

        assertEquals(DEFAULT_TDS_HASH_VALUE, tdsContent.hashCode())
    }

    companion object {
        private const val DEFAULT_TDS_HASH_VALUE = -160289387
    }
}
