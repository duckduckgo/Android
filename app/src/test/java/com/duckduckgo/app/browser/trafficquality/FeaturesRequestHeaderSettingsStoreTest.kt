/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.app.browser.trafficquality.remote.TrafficQualitySettingsJson
import com.squareup.moshi.Moshi
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.Test

class FeaturesRequestHeaderSettingsStoreTest {

    private val moshi = Moshi.Builder().build()

    @Test
    fun jsonCanBeParsed() {
        val jsonString = """
            {
                "versions": [
                    {
                        "appVersion": 52200000,
                        "daysUntilLoggingStarts": 5,
                        "daysLogging": 10,
                        "features": {
                            "gpc": true,
                            "cpm": false,
                            "appTp": true,
                            "netP": false
                        }
                    }
                ]
            }
     """
        val jsonAdapter = moshi.adapter(TrafficQualitySettingsJson::class.java)
        val rootObject = jsonAdapter.fromJson(jsonString)
        assertNotNull(rootObject)
        assertEquals(1, rootObject?.versions?.size)

        val version = rootObject?.versions?.first()
        assertNotNull(version)
        assertEquals(52200000, version?.appVersion)
        assertEquals(5, version?.daysUntilLoggingStarts)
        assertEquals(10, version?.daysLogging)
    }
}
