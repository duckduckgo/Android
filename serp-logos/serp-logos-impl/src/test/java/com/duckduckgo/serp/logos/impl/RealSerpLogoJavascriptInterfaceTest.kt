/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.serp.logos.impl

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RealSerpLogoJavascriptInterfaceTest {

    private lateinit var testee: RealSerpLogoJavascriptInterface

    @Before
    fun setUp() {
        testee = RealSerpLogoJavascriptInterface()
    }

    @Test
    fun whenAccessingJsPropertyThenReturnsValidJavaScript() {
        val result = testee.js

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        assertTrue(result.contains("document.querySelector"))
        assertTrue(result.contains("js-logo-ddg"))
    }

    @Test
    fun whenAccessingJsPropertyThenContainsExpectedLogic() {
        val result = testee.js

        assertTrue(result.contains("element.dataset.dynamicLogo"))
        assertTrue(result.contains("easterEgg|"))
        assertTrue(result.contains("normal|"))
        assertTrue(result.contains("window.getComputedStyle"))
        assertTrue(result.contains("backgroundImage"))
    }

    @Test
    fun whenAccessingJsPropertyThenReturnsConsistentValue() {
        val result1 = testee.js
        val result2 = testee.js

        assertTrue(result1 == result2)
    }
}
