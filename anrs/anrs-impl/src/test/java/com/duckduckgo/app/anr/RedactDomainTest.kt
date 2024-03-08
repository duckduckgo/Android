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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RedactDomainTest {

    @Test
    fun `redact PII from stack trace`() {
        val message = """
            Exception in thread "main" java.lang.NullPointerException: Something went wrong
                at com.example.MyClass.method(MyClass.java:10)
                at com.example.AnotherClass.anotherMethod(AnotherClass.java:20)
                at com.example.Main.main(Main.java:5)
                Caused by: java.lang.IllegalArgumentException: Invalid input: john@example.com
                    at com.example.Helper.validateEmail(Helper.java:15)
                    at com.example.MyClass.method(MyClass.java:8)
                    at com.example.Main.main(Main.java:10)
                    ... 2 more
                Caused by: java.lang.IllegalArgumentException: Invalid input: john.doe@example.com
                    at com.example.Helper.validateEmail(Helper.java:15)
                    at com.example.MyClass.method(MyClass.java:8)
                    at com.example.Main.main(Main.java:10)
                    ... 2 more
                Caused by: java.lang.IllegalArgumentException: Invalid input: john.doe.doe@example.com
                    at com.example.Helper.validateEmail(Helper.java:15)
                    at com.example.MyClass.method(MyClass.java:8)
                    at com.example.Main.main(Main.java:10)
                    ... 2 more
                Caused by: java.net.MalformedURLException: Invalid URL: https://example.com/user/profile?id=123456
                    at com.example.Helper.validateUrl(Helper.java:25)
                    ... 3 more
                Caused by: java.lang.NumberFormatException: For input string: "555-123-4567"
                    at java.base/java.lang.Integer.parseInt(Integer.java:652)
                    at java.base/java.lang.Integer.parseInt(Integer.java:770)
                    at com.example.Helper.validatePhoneNumber(Helper.java:35)
                    ... 4 more
                Caused by: java.lang.NumberFormatException: For input string: "5551234567"
                    at java.base/java.lang.Integer.parseInt(Integer.java:652)
                    at java.base/java.lang.Integer.parseInt(Integer.java:770)
                    at com.example.Helper.validatePhoneNumber(Helper.java:40)
                    ... 5 more
                Caused by: java.lang.NumberFormatException: For input string: "+1 (555) 123-4567"
                    at java.base/java.lang.Integer.parseInt(Integer.java:652)
                    at java.base/java.lang.Integer.parseInt(Integer.java:770)
                    at com.example.Helper.validatePhoneNumber(Helper.java:45)
                    ... 6 more
        """.trimIndent()

        val actual = message.sanitizeStackTrace()
        val expected = message
            .replace("https://example.com/user/profile?id=123456", "[REDACTED_URL]")
            .replace("555-123-4567", "[REDACTED_PHONE]")
            .replace("5551234567", "[REDACTED_PHONE]")
            .replace("1 (555) 123-4567", "[REDACTED_PHONE]")
            .replace("john.doe@example.com", "[REDACTED_EMAIL]")
            .replace("john.doe.doe@example.com", "[REDACTED_EMAIL]")
            .replace("john@example.com", "[REDACTED_EMAIL]")
        assertEquals(expected, actual)
    }
}
