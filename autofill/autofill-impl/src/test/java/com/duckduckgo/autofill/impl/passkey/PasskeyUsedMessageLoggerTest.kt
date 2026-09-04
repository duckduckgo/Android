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
import logcat.LogPriority
import logcat.LogcatLogger
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric so the temporary android.util.Log diagnostic in PasskeyUsedMessageLogger resolves.
@RunWith(AndroidJUnit4::class)
class PasskeyUsedMessageLoggerTest {

    private val logged = mutableListOf<String>()
    private val testee = PasskeyUsedMessageLogger()

    @Before
    fun setup() {
        LogcatLogger.install(
            object : LogcatLogger {
                override fun log(
                    priority: LogPriority,
                    tag: String,
                    message: String,
                ) {
                    logged.add(message)
                }
            },
        )
    }

    @After
    fun tearDown() {
        LogcatLogger.uninstall()
    }

    @Test
    fun whenAuthenticationSucceedsThenOutcomeIsLogged() {
        testee.logUsed(JSONObject().put("type", "get"))

        assertEquals(listOf("Passkey: get succeeded"), logged)
    }

    @Test
    fun whenRegistrationSucceedsThenOutcomeIsLogged() {
        testee.logUsed(JSONObject().put("type", "create"))

        assertEquals(listOf("Passkey: create succeeded"), logged)
    }

    @Test
    fun whenCeremonyFailsWithErrorThenErrorNameIsLogged() {
        testee.logFailed(JSONObject().put("type", "create").put("error", "NotReadableError"))

        assertEquals(listOf("Passkey: create failed with NotReadableError"), logged)
    }

    @Test
    fun whenCeremonyFailsWithoutErrorThenFailureIsStillLogged() {
        testee.logFailed(JSONObject().put("type", "get"))

        assertEquals(listOf("Passkey: get failed with unspecified"), logged)
    }

    @Test
    fun whenTypeIsUnsupportedThenNoOutcomeIsLogged() {
        testee.logUsed(JSONObject().put("type", "password"))

        assertTrue(logged.none { it.startsWith("Passkey: password") })
    }

    @Test
    fun whenTypeIsMissingThenNoOutcomeIsLogged() {
        testee.logUsed(JSONObject())

        assertTrue(logged.none { it.contains("succeeded") })
    }
}
