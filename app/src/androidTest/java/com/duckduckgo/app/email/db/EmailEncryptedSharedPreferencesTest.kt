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

package com.duckduckgo.app.email.db

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import javax.inject.Provider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

@FlowPreview
class EmailEncryptedSharedPreferencesTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockPixel: Pixel = mock()
    lateinit var testee: EmailEncryptedSharedPreferences
    private val createPreferencesAsyncProvider = object : Provider<Boolean> {
        override fun get(): Boolean {
            return true
        }
    }

    @Before
    fun before() {
        testee = EmailEncryptedSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
            mockPixel,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
            createPreferencesAsyncProvider,
        )
    }

    @Test
    fun whenNextAliasEqualsValueThenValueIsSentToNextAliasChannel() = runTest {
        testee.setNextAlias("test")

        assertEquals("test", testee.getNextAlias())
    }

    @Test
    fun whenNextAliasEqualsNullThenNullIsSentToNextAliasChannel() = runTest {
        testee.setNextAlias(null)

        assertNull(testee.getNextAlias())
    }
}
