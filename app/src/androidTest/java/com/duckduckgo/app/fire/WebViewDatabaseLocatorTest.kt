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

package com.duckduckgo.app.fire

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewDatabaseLocatorTest {

    @Test
    fun whenGetDatabasePathOnDeviceThenPathNotEmpty() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val webViewDatabaseLocator = WebViewDatabaseLocator(context)

        val databasePath = webViewDatabaseLocator.getDatabasePath()

        // If this test fails, it means WebViewDatabase path has changed its location
        // If so, add a new database location to knownLocations list
        assertTrue(databasePath.isNotEmpty())
    }

    @Test
    fun whenDatabasePathFoundThenReturnedAbsolutePathToFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dataDir = context.applicationInfo.dataDir
        val webViewDatabaseLocator = WebViewDatabaseLocator(context)

        val databasePath = webViewDatabaseLocator.getDatabasePath()

        assertTrue(databasePath.startsWith(dataDir))
    }

    @Test
    fun whenDatabasePathNotFoundThenReturnsEmpty() {
        val mockApplicationInfo = mock<ApplicationInfo>().apply { dataDir = "nonExistingDir" }
        val context = mock<Context> { on { applicationInfo } doReturn mockApplicationInfo }
        val webViewDatabaseLocator = WebViewDatabaseLocator(context)

        val databasePath = webViewDatabaseLocator.getDatabasePath()

        assertTrue(databasePath.isEmpty())
    }
}
