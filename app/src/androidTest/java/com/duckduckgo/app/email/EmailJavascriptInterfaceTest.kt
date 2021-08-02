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

package com.duckduckgo.app.email

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EmailJavascriptInterfaceTest {

    private val mockEmailManager: EmailManager = mock()
    lateinit var testee: EmailJavascriptInterface
    private var counter = 0

    @Before
    fun setup() {
        testee = EmailJavascriptInterface(mockEmailManager) { counter++ }
    }

    @Test
    fun whenIsSignedInThenIsSignedInCalled() {
        testee.isSignedIn()

        verify(mockEmailManager).isSignedIn()
    }

    @Test
    fun whenStoreCredentialsThenStoreCredentialsCalledWithCorrectParameters() {
        testee.storeCredentials("token", "username", "cohort")

        verify(mockEmailManager).storeCredentials("token", "username", "cohort")
    }

    @Test
    fun whenShowTooltipThenLambdaCalled() {
        testee.showTooltip()

        assertEquals(1, counter)
    }

    private fun givenAliasExists() {
        whenever(mockEmailManager.getAlias()).thenReturn("alias")
    }

    private fun givenAliasDoesNotExist() {
        whenever(mockEmailManager.getAlias()).thenReturn("")
    }
}
