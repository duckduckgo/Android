package com.duckduckgo.app.pixels.ppropromo.params

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
    fun whenCredentialsStoredIsMoreThan5ThenPluginShouldReturnParamTrue() = runTest {
        val internalAutofillStore: InternalAutofillStore = mock()
        val plugin = AutofillUserAdditionalPixelParamPlugin(internalAutofillStore)
        whenever(internalAutofillStore.getCredentialCount()).thenReturn(flowOf(10))

        assertEquals("autofillUser" to "true", plugin.params())
    }

    @Test
    fun whenCredentialsStoredIsLessThan5ThenPluginShouldReturnParamFalse() = runTest {
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
