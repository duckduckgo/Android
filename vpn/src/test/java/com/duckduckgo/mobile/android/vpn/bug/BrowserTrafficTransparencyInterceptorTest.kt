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

package com.duckduckgo.mobile.android.vpn.bug

import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.RequestTrackerType
import org.junit.Assert
import org.junit.Before
import org.junit.Test

private const val HOSTNAME = "hostname"
private const val DDG_PACKAGE_ID = "com.duckduckgo.mobile"
private const val OTHER_PACKAGE_ID = "other.package.id"

class BrowserTrafficTransparencyInterceptorTest {
    private lateinit var browserTrafficTransparencyInterceptor: BrowserTrafficTransparencyInterceptor

    @Before
    fun before() {
        browserTrafficTransparencyInterceptor = BrowserTrafficTransparencyInterceptor()
    }

    @Test
    fun whenInterceptingDdgPackageIdThenReturnNotTracker() {
        val trackerType = browserTrafficTransparencyInterceptor.interceptTrackerRequest(HOSTNAME, DDG_PACKAGE_ID)
        Assert.assertEquals(trackerType, RequestTrackerType.NotTracker(HOSTNAME))
    }

    @Test
    fun whenInterceptingNotDdgPackageIdThenReturnNull() {
        val trackerType = browserTrafficTransparencyInterceptor.interceptTrackerRequest(HOSTNAME, OTHER_PACKAGE_ID)
        Assert.assertNull(trackerType)
    }
}
