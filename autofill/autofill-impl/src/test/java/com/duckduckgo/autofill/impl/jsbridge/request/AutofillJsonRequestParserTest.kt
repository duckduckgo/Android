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

import com.duckduckgo.autofill.impl.jsbridge.request.FormSubmissionTriggerType.FORM_SUBMISSION
import com.duckduckgo.autofill.impl.jsbridge.request.FormSubmissionTriggerType.PARTIAL_SAVE
import com.duckduckgo.autofill.impl.jsbridge.request.FormSubmissionTriggerType.UNKNOWN
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AutofillJsonRequestParserTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val testee = AutofillJsonRequestParser(dispatchers = coroutineTestRule.testDispatcherProvider)

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
    fun whenPasswordMissingAndIsPartialSaveThenUsernamePopulatedAndPartialSaveFlagSet() = runTest {
        val parsed = "storeFormData_passwordMissing_partialSave".parseStoreFormDataJson()
        assertEquals("dax@duck.com", parsed.credentials!!.username)
        assertNull(parsed.credentials!!.password)
        assertEquals(PARTIAL_SAVE, parsed.trigger)
    }

    @Test
    fun whenTopLevelCredentialsObjectMissingThenParsesWithoutError() = runTest {
        val parsed = "storeFormData_topLevelDataMissing".parseStoreFormDataJson()
        assertNull(parsed.credentials)
    }

    @Test
    fun whenStoreFormDataRequestIsEmptyThenExceptionThrown() = runTest {
        val result = testee.parseStoreFormDataRequest("")
        assertTrue(result.isFailure)
    }

    @Test
    fun whenStoreFormDataRequestIsMalformedJSONThenExceptionThrown() = runTest {
        val result = testee.parseStoreFormDataRequest("invalid json")
        assertTrue(result.isFailure)
    }

    @Test
    fun whenStoreFormDataRequestMissingTriggerThenIsUnknown() = runTest {
        val parsed = "storeFormData_trigger_missing".parseStoreFormDataJson()
        assertEquals(UNKNOWN, parsed.trigger)
    }

    @Test
    fun whenStoreFormDataRequestUnknownTriggerThenIsUnknown() = runTest {
        val parsed = "storeFormData_trigger_unknown".parseStoreFormDataJson()
        assertEquals(UNKNOWN, parsed.trigger)
    }

    @Test
    fun whenStoreFormDataRequestHasFormSubmissionTriggerThenIsPopulated() = runTest {
        val parsed = "storeFormData_trigger_formSubmission".parseStoreFormDataJson()
        assertEquals(FORM_SUBMISSION, parsed.trigger)
    }

    @Test
    fun whenStoreFormDataRequestHasPartialSaveTriggerThenIsPopulated() = runTest {
        val parsed = "storeFormData_trigger_partialSave".parseStoreFormDataJson()
        assertEquals(PARTIAL_SAVE, parsed.trigger)
    }

    private suspend fun String.parseStoreFormDataJson(): AutofillStoreFormDataRequest {
        val json = this.loadJsonFile()
        assertNotNull("Failed to load specified JSON file: $this")
        return testee.parseStoreFormDataRequest(json).getOrThrow()
    }

    private fun String.loadJsonFile(): String {
        return FileUtilities.loadText(
            AutofillJsonRequestParserTest::class.java.classLoader!!,
            "json/$this.json",
        )
    }
}
