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

package com.duckduckgo.app.anr

import androidx.test.ext.junit.runners.AndroidJUnit4
import logcat.asLog
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.RuntimeException

@RunWith(AndroidJUnit4::class)
class RedactDomainTest {

    @Test
    fun whenAllDomainsMultilinePresentThenRedact() {
        val t = try {
            throw RuntimeException("""
                exception with https://redact.me and http://redact.me and redact.me and www.redact.me domains
                and http://www.redact.me
            """.trimIndent())
        } catch (t: Throwable) {
            t
        }
        val actual = t.asLog()
        val expected = actual
            .replace("https://redact.me", "[REDACTED]")
            .replace("http://redact.me", "[REDACTED]")
            .replace("http://www.redact.me", "[REDACTED]")

        assertEquals(expected, actual.redactDomains())
    }
}
