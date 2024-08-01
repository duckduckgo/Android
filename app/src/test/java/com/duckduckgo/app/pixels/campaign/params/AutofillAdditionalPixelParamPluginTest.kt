/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.pixels.campaign.params

import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AutofillAdditionalPixelParamPluginTest {
    @Test
    fun whenCredentialsStoredIsMoreThan10ThenPluginShouldReturnParamTrue() = runTest {
        val internalAutofillStore: InternalAutofillStore = mock()
        val plugin = AutofillUserAdditionalPixelParamPlugin(internalAutofillStore)
        whenever(internalAutofillStore.getCredentialCount()).thenReturn(flowOf(15))

        assertEquals("autofillUser" to "true", plugin.params())
    }

    @Test
    fun whenCredentialsStoredIs10ThenPluginShouldReturnParamFalse() = runTest {
        val internalAutofillStore: InternalAutofillStore = mock()
        val plugin = AutofillUserAdditionalPixelParamPlugin(internalAutofillStore)
        whenever(internalAutofillStore.getCredentialCount()).thenReturn(flowOf(10))

        assertEquals("autofillUser" to "false", plugin.params())
    }

    @Test
    fun whenCredentialsStoredIsLessThan10ThenPluginShouldReturnParamFalse() = runTest {
        val internalAutofillStore: InternalAutofillStore = mock()
        val plugin = AutofillUserAdditionalPixelParamPlugin(internalAutofillStore)
        whenever(internalAutofillStore.getCredentialCount()).thenReturn(flowOf(3))

        assertEquals("autofillUser" to "false", plugin.params())
    }

    @Test
    fun whenHasNoCredentialsStoredThenPluginShouldReturnParamFalse() = runTest {
        val internalAutofillStore: InternalAutofillStore = mock()
        val plugin = AutofillUserAdditionalPixelParamPlugin(internalAutofillStore)
        whenever(internalAutofillStore.getCredentialCount()).thenReturn(emptyFlow())

        assertEquals("autofillUser" to "false", plugin.params())
    }
}
