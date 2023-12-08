/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.sharedcreds

import com.duckduckgo.autofill.impl.sharedcreds.SharedCredentialsParser.SharedCredentialConfig
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.common.test.CoroutineTestRule
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AppleSharedCredentialsParserTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val moshi = Moshi.Builder().build()
    private val jsonReader: SharedCredentialJsonReader = mock()
    private val autofillUrlMatcher: AutofillUrlMatcher = mock()

    private val testee = AppleSharedCredentialsParser(
        moshi = moshi,
        jsonReader = jsonReader,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        autofillUrlMatcher = autofillUrlMatcher,
    )

    @Test
    fun whenJsonNotReadThenRulesAreEmpty() = runTest {
        whenever(jsonReader.read()).thenReturn(null)
        val rules = testee.read()
        rules.assertRulesAreEmpty()
    }

    @Test
    fun whenJsonIsCorruptNotReadThenRulesAreEmpty() = runTest {
        whenever(jsonReader.read()).thenReturn("not valid json")
        val rules = testee.read()
        rules.assertRulesAreEmpty()
    }
}

private fun SharedCredentialConfig.assertRulesAreEmpty() {
    assertTrue(omnidirectionalRules.isEmpty())
    assertTrue(unidirectionalRules.isEmpty())
}
