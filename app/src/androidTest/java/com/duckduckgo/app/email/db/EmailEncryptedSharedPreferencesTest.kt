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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@FlowPreview
@ExperimentalCoroutinesApi
class EmailEncryptedSharedPreferencesTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    lateinit var testee: EmailEncryptedSharedPreferences

    @Before
    fun before() {
        testee = EmailEncryptedSharedPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun whenNextAliasEqualsValueThenValueIsSentToNextAliasChannel() = coroutineRule.runBlocking {
        testee.nextAlias = "test"

        assertEquals("test", testee.nextAliasFlow().first())
    }

    @Test
    fun whenNextAliasEqualsNullThenNullIsSentToNextAliasChannel() = coroutineRule.runBlocking {
        testee.nextAlias = null

        assertNull(testee.nextAliasFlow().first())
    }
}
