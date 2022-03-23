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

package com.duckduckgo.app.fire

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.DefaultWebViewDatabaseProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class AuthDatabaseLocatorTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val authDatabaseLocator = AuthDatabaseLocator(context)

    @Before
    fun before() {
        DefaultWebViewDatabaseProvider(context).get()
    }

    @Test
    fun whenGetDatabasePathOnDeviceThenPathNotEmpty() = runTest {
        awaitPathCreation()
        val databasePath = authDatabaseLocator.getDatabasePath()

        // If this test fails, it means the Auth Database path has changed its location
        // If so, add a new database location to knownLocations list
        assertTrue(databasePath.isNotEmpty())
    }

    private suspend fun awaitPathCreation() {
        withTimeout(5000) {
            while (authDatabaseLocator.getDatabasePath().isEmpty()) {
                delay(10)
            }
        }
    }
}
