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

package com.duckduckgo.autofill.impl.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.service.AutofillFieldType.PASSWORD
import com.duckduckgo.autofill.impl.service.AutofillFieldType.USERNAME
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealAutofillProviderSuggestionsTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val appBuildConfig = mock<AppBuildConfig>().apply {
        whenever(sdkInt).thenReturn(30)
    }

    private val autofillStore = mock<AutofillStore>()

    private val mockViewProvider = mock<AutofillServiceViewProvider>()

    private val suggestionFormatter = mock<AutofillServiceSuggestionCredentialFormatter>().apply {
        whenever(this.getSuggestionSpecs(any())).thenReturn(SuggestionUISpecs("title", "subtitle", 0))
        whenever(this.getOpenDuckDuckGoSuggestionSpecs()).thenReturn(SuggestionUISpecs("Search in DuckDuckGo", "", 0))
    }

    private val testee = RealAutofillProviderSuggestions(
        appBuildConfig = appBuildConfig,
        autofillStore = autofillStore,
        viewProvider = mockViewProvider,
        suggestionsFormatter = suggestionFormatter,
    )

    @Test
    fun whenRunningOnAndroidVersionWithoutInlineSupportThenDoNotAddInlineSuggestions() = runTest {
        val credentials = listOf(
            LoginCredentials(1L, "username", "password", "example.com"),
        )
        whenever(appBuildConfig.sdkInt).thenReturn(29)
        whenever(autofillStore.getCredentials(any())).thenReturn(credentials)
        whenever(mockViewProvider.createFormPresentation(any(), any(), any(), any())).thenReturn(mock())
        whenever(mockViewProvider.createInlinePresentation(any(), any(), any(), any(), any(), any())).thenReturn(null)

        testee.buildSuggestionsResponse(
            context = context,
            AutofillRootNode(
                "com.example.app",
                null,
                listOf(
                    ParsedAutofillField(autofillId(), "", "", "", USERNAME, viewNode()),
                ),
            ),
            mock(),
        )

        verify(mockViewProvider, times(0)).createInlinePresentation(any(), any(), any(), any(), any(), any())
        verify(mockViewProvider, times(2)).createFormPresentation(any(), any(), any(), any())
    }

    @Test
    fun whenNoInlinePresentationSpecsThenDoNotAddInlineSuggestions() = runTest {
        val credentials = listOf(
            LoginCredentials(1L, "username", "password", "example.com"),
        )
        whenever(autofillStore.getCredentials(any())).thenReturn(credentials)
        whenever(mockViewProvider.createFormPresentation(any(), any(), any(), any())).thenReturn(mock())
        whenever(mockViewProvider.createInlinePresentation(any(), any(), any(), any(), any(), any())).thenReturn(null)

        testee.buildSuggestionsResponse(
            context = context,
            AutofillRootNode(
                "com.example.app",
                null,
                listOf(
                    ParsedAutofillField(autofillId(), "", "", "", USERNAME, viewNode()),
                ),
            ),
            fillRequest().inlineSuggestionsRequest(
                inlineSuggestionsRequest().maxSuggestionCount(3),
            ),
        )

        verify(mockViewProvider, times(0)).createInlinePresentation(any(), any(), any(), any(), any(), any())
        verify(mockViewProvider, times(2)).createFormPresentation(any(), any(), any(), any())
    }

    @Test
    fun whenManyCredentialSuggestionsAvailableThenShowAsManyAsPossiblePlusDDGSearch() = runTest {
        val credentials = listOf(
            LoginCredentials(1L, "username", "password", "example.com"),
            LoginCredentials(2L, "username2", "password2", "example2.com"),
            LoginCredentials(3L, "username3", "password3", "example3.com"),
            LoginCredentials(4L, "username4", "password4", "example4.com"),
        )
        whenever(autofillStore.getCredentials(any())).thenReturn(credentials)
        whenever(mockViewProvider.createFormPresentation(any(), any(), any(), any())).thenReturn(mock())
        whenever(mockViewProvider.createInlinePresentation(any(), any(), any(), any(), any(), any())).thenReturn(mock())

        testee.buildSuggestionsResponse(
            context = context,
            AutofillRootNode(
                "com.example.app",
                null,
                listOf(
                    ParsedAutofillField(autofillId(), "", "", "", USERNAME, viewNode()),
                ),
            ),
            fillRequest().inlineSuggestionsRequest(
                inlineSuggestionsRequest()
                    .maxSuggestionCount(3)
                    .inlinePresentationSpecs(inlinePresentationSpec()),
            ),
        )

        verify(mockViewProvider, times(3)).createInlinePresentation(any(), any(), any(), any(), any(), any())
        verify(mockViewProvider, times(5)).createFormPresentation(any(), any(), any(), any())
    }

    @Test
    fun whenSupportedThenInlineSuggestionsAdded() = runTest {
        val credentials = listOf(
            LoginCredentials(1L, "username", "password", "example.com"),
            LoginCredentials(2L, "username2", "password2", "example2.com"),
        )
        whenever(autofillStore.getCredentials(any())).thenReturn(credentials)
        whenever(mockViewProvider.createFormPresentation(any(), any(), any(), any())).thenReturn(mock())
        whenever(mockViewProvider.createInlinePresentation(any(), any(), any(), any(), any(), any())).thenReturn(mock())

        testee.buildSuggestionsResponse(
            context = context,
            AutofillRootNode(
                "com.example.app",
                null,
                listOf(
                    ParsedAutofillField(autofillId(), "", "", "", USERNAME, viewNode()),
                ),
            ),
            fillRequest().inlineSuggestionsRequest(
                inlineSuggestionsRequest()
                    .maxSuggestionCount(3)
                    .inlinePresentationSpecs(inlinePresentationSpec()),
            ),
        )

        verify(mockViewProvider, times(3)).createInlinePresentation(any(), any(), any(), any(), any(), any())
        verify(mockViewProvider, times(3)).createFormPresentation(any(), any(), any(), any())
    }

    @Test
    fun whenCredentialsFoundThenAddSuggestions() = runTest {
        val credentials = listOf(
            LoginCredentials(1L, "username", "password", "example.com"),
            LoginCredentials(2L, "username2", "password2", "example2.com"),
        )
        whenever(autofillStore.getCredentials(any())).thenReturn(credentials)
        whenever(mockViewProvider.createFormPresentation(any(), any(), any(), any())).thenReturn(mock())

        testee.buildSuggestionsResponse(
            context = context,
            AutofillRootNode(
                "com.example.app",
                null,
                listOf(
                    ParsedAutofillField(autofillId(), "", "", "", USERNAME, viewNode()),
                ),
            ),
            mock(),
        )

        verify(mockViewProvider, times(3)).createFormPresentation(any(), any(), any(), any())
    }

    @Test
    fun whenNoCredentialsFoundThenOnlyOpenDDGAppItemIsAdded() = runTest {
        whenever(autofillStore.getCredentials(any())).thenReturn(emptyList())
        whenever(mockViewProvider.createFormPresentation(any(), any(), any(), any())).thenReturn(mock())

        testee.buildSuggestionsResponse(
            context = context,
            AutofillRootNode(
                "com.example.app",
                null,
                listOf(
                    ParsedAutofillField(autofillId(), "", "", "", USERNAME, viewNode()),
                ),
            ),
            mock(),
        )

        verify(mockViewProvider, times(1)).createFormPresentation(any(), any(), any(), any())
    }

    @Test
    fun whenMultipleFillableFieldsFoundThenAddSuggestionsForEach() = runTest {
        val credentials = listOf(
            LoginCredentials(1L, "username", "password", "example.com"),
        )
        whenever(autofillStore.getCredentials(any())).thenReturn(credentials)
        whenever(mockViewProvider.createFormPresentation(any(), any(), any(), any())).thenReturn(mock())

        testee.buildSuggestionsResponse(
            context = context,
            AutofillRootNode(
                "com.example.app",
                null,
                listOf(
                    ParsedAutofillField(autofillId(), "", "", "", USERNAME, viewNode()),
                    ParsedAutofillField(autofillId(), "", "", "", PASSWORD, viewNode()),
                ),
            ),
            mock(),
        )

        verify(mockViewProvider, times(3)).createFormPresentation(any(), any(), any(), any())
    }

    @Test
    fun whenMultipleFillableFieldsFoundThenRespectInlineLimitsPerFillableField() = runTest {
        val credentials = listOf(
            LoginCredentials(1L, "username", "password", "example.com"),
            LoginCredentials(2L, "username2", "password2", "example2.com"),
        )
        whenever(autofillStore.getCredentials(any())).thenReturn(credentials)
        whenever(mockViewProvider.createFormPresentation(any(), any(), any(), any())).thenReturn(mock())
        whenever(mockViewProvider.createInlinePresentation(any(), any(), any(), any(), any(), any())).thenReturn(mock())

        testee.buildSuggestionsResponse(
            context = context,
            AutofillRootNode(
                "com.example.app",
                null,
                listOf(
                    ParsedAutofillField(autofillId(), "", "", "", USERNAME, viewNode()),
                    ParsedAutofillField(autofillId(), "", "", "", PASSWORD, viewNode()),
                ),
            ),
            fillRequest().inlineSuggestionsRequest(
                inlineSuggestionsRequest()
                    .maxSuggestionCount(3)
                    .inlinePresentationSpecs(inlinePresentationSpec()),
            ),
        )

        verify(mockViewProvider, times(5)).createInlinePresentation(any(), any(), any(), any(), any(), any())
        verify(mockViewProvider, times(5)).createFormPresentation(any(), any(), any(), any())
    }

    @Test
    fun whenMultipleFillableFieldsFoundThenAddTheRightSuggestionValueForEach() = runTest {
        val credential = spy(LoginCredentials(1L, "username", "password", "example.com"))
        val credentials = listOf(credential)
        whenever(autofillStore.getCredentials(any())).thenReturn(credentials)
        whenever(mockViewProvider.createFormPresentation(any(), any(), any(), any())).thenReturn(mock())

        testee.buildSuggestionsResponse(
            context = context,
            AutofillRootNode(
                "com.example.app",
                null,
                listOf(
                    ParsedAutofillField(autofillId(), "", "", "", USERNAME, viewNode()),
                    ParsedAutofillField(autofillId(), "", "", "", PASSWORD, viewNode()),
                ),
            ),
            mock(),
        )

        verify(credential, times(1)).username
        verify(credential, times(1)).password
        verify(mockViewProvider, times(3)).createFormPresentation(any(), any(), any(), any())
    }
}
