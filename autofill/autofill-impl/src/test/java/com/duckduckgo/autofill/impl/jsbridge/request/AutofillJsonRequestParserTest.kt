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

package com.duckduckgo.autofill.impl.jsbridge.request

import com.duckduckgo.common.test.FileUtilities
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@ExperimentalCoroutinesApi
class AutofillJsonRequestParserTest {

    private val moshi = Moshi.Builder().build()
    private val testee = AutofillJsonRequestParser(moshi)

    @Test
    fun whenUsernameAndPasswordBothProvidedThenBothInResponse() = runTest {
        val parsed = "storeFormData_usernameAndPasswordProvided".parseStoreFormDataJson()
        assertEquals("dax@duck.com", parsed.credentials!!.username)
        assertEquals("123456", parsed.credentials!!.password)
    }

    @Test
    fun whenUsernameAndPasswordBothMissingThenBothAreNull() = runTest {
        val parsed = "storeFormData_usernameAndPasswordMissing".parseStoreFormDataJson()
        assertNull(parsed.credentials!!.username)
        assertNull(parsed.credentials!!.password)
    }

    @Test
    fun whenUsernameAndPasswordBothNullThenBothAreNullInParsedObject() = runTest {
        val parsed = "storeFormData_usernameAndPasswordNull".parseStoreFormDataJson()
        assertNull(parsed.credentials!!.username)
        assertNull(parsed.credentials!!.password)
    }

    @Test
    fun whenAdditionalUnknownPropertiesInRequestThenStillParses() = runTest {
        val parsed = "storeFormData_additionalUnknownPropertiesIncluded".parseStoreFormDataJson()
        assertEquals("dax@duck.com", parsed.credentials!!.username)
        assertEquals("123456", parsed.credentials!!.password)
    }

    @Test
    fun whenUsernameMissingThenPasswordPopulated() = runTest {
        val parsed = "storeFormData_usernameMissing".parseStoreFormDataJson()
        assertNull(parsed.credentials!!.username)
        assertEquals("123456", parsed.credentials!!.password)
    }

    @Test
    fun whenPasswordMissingThenUsernamePopulated() = runTest {
        val parsed = "storeFormData_passwordMissing".parseStoreFormDataJson()
        assertEquals("dax@duck.com", parsed.credentials!!.username)
        assertNull(parsed.credentials!!.password)
    }

    @Test
    fun whenTopLevelCredentialsObjectMissingThenParsesWithoutError() = runTest {
        val parsed = "storeFormData_topLevelDataMissing".parseStoreFormDataJson()
        assertNull(parsed.credentials)
    }

    private suspend fun String.parseStoreFormDataJson(): AutofillStoreFormDataRequest {
        val json = this.loadJsonFile()
        assertNotNull("Failed to load specified JSON file: $this")
        return testee.parseStoreFormDataRequest(json)
    }

    private fun String.loadJsonFile(): String {
        return FileUtilities.loadText(
            AutofillJsonRequestParserTest::class.java.classLoader!!,
            "json/$this.json",
        )
    }
}
