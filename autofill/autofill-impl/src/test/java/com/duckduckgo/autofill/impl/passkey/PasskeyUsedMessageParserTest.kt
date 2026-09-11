/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.autofill.impl.passkey

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.passkey.PasskeyUsageError.NotAllowedError
import com.duckduckgo.autofill.impl.passkey.PasskeyUsageError.UnknownError
import com.duckduckgo.autofill.impl.passkey.PasskeyUsageResult.CreateFailed
import com.duckduckgo.autofill.impl.passkey.PasskeyUsageResult.Created
import com.duckduckgo.autofill.impl.passkey.PasskeyUsageResult.UseFailed
import com.duckduckgo.autofill.impl.passkey.PasskeyUsageResult.Used
import com.duckduckgo.js.messaging.api.JsMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasskeyUsedMessageParserTest {

    private val testee = PasskeyUsedMessageParser()

    @Test
    fun whenPasskeyUsedWithCreateThenReturnsCreated() {
        val result = testee.parse(message(method = "passkeyUsed", type = "create"))

        assertEquals(Created, result)
    }

    @Test
    fun whenPasskeyUsedWithGetThenReturnsUsed() {
        val result = testee.parse(message(method = "passkeyUsed", type = "get"))

        assertEquals(Used, result)
    }

    @Test
    fun whenPasskeyFailedWithGetThenReturnsUseFailed() {
        val result = testee.parse(message(method = "passkeyFailed", type = "get", error = "NotAllowedError"))

        assertEquals(UseFailed(NotAllowedError), result)
    }

    @Test
    fun whenPasskeyFailedWithCreateThenReturnsCreateFailed() {
        val result = testee.parse(message(method = "passkeyFailed", type = "create", error = "InvalidStateError"))

        assertEquals(CreateFailed(PasskeyUsageError.InvalidStateError), result)
    }

    @Test
    fun whenPasskeyFailedWithAllowlistedErrorThenMapsToThatError() {
        PasskeyUsageError.entries.forEach { error ->
            val result = testee.parse(message(method = "passkeyFailed", type = "get", error = error.name))

            assertEquals(UseFailed(error), result)
        }
    }

    @Test
    fun whenPasskeyFailedWithMissingErrorThenReturnsUnknownError() {
        val result = testee.parse(message(method = "passkeyFailed", type = "get"))

        assertEquals(UseFailed(UnknownError), result)
    }

    @Test
    fun whenPasskeyFailedWithEmptyErrorThenReturnsUnknownError() {
        val result = testee.parse(message(method = "passkeyFailed", type = "create", error = ""))

        assertEquals(CreateFailed(UnknownError), result)
    }

    @Test
    fun whenPasskeyFailedWithHostileErrorThenReturnsCreateFailedWithUnknownError() {
        val result = testee.parse(message(method = "passkeyFailed", type = "create", error = "HostileString"))

        assertEquals(CreateFailed(UnknownError), result)
    }

    @Test
    fun whenPasskeyFailedWithCssOtherThenReturnsCreateFailedWithUnknownError() {
        val result = testee.parse(message(method = "passkeyFailed", type = "create", error = "Other"))

        assertEquals(CreateFailed(UnknownError), result)
    }

    @Test
    fun whenPasskeyUsedHasUnusedErrorParamThenIgnoresIt() {
        val result = testee.parse(message(method = "passkeyUsed", type = "get", error = "NotAllowedError"))

        assertEquals(Used, result)
    }

    @Test
    fun whenPasskeyUsedTypeIsUnsupportedThenReturnsNull() {
        val result = testee.parse(message(method = "passkeyUsed", type = "unknown"))

        assertNull(result)
    }

    @Test
    fun whenPasskeyFailedTypeIsUnsupportedThenReturnsNull() {
        val result = testee.parse(message(method = "passkeyFailed", type = "unknown", error = "NotAllowedError"))

        assertNull(result)
    }

    @Test
    fun whenTypeIsMissingThenReturnsNull() {
        val result = testee.parse(message(method = "passkeyUsed", type = ""))

        assertNull(result)
    }

    @Test
    fun whenMethodIsUnsupportedThenReturnsNull() {
        val result = testee.parse(message(method = "passkeyMaybe", type = "get"))

        assertNull(result)
    }

    private fun message(
        method: String,
        type: String,
        error: String? = null,
    ): JsMessage {
        val params = JSONObject().put("type", type)
        if (error != null) {
            params.put("error", error)
        }
        return JsMessage(
            context = "contentScopeScripts",
            featureName = "webCompat",
            method = method,
            params = params,
            id = null,
        )
    }
}
