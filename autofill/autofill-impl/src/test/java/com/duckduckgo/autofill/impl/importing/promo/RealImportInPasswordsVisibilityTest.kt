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

package com.duckduckgo.autofill.impl.importing.promo

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.importing.capability.ImportGooglePasswordsCapabilityChecker
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealImportInPasswordsVisibilityTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val internalAutofillStore: InternalAutofillStore = mock()
    private val autofillFeature: AutofillFeature = mock()
    private val importPasswordCapabilityChecker: ImportGooglePasswordsCapabilityChecker = mock()
    private val appCoroutineScope: CoroutineScope = coroutineTestRule.testScope
    private val dispatcherProvider = coroutineTestRule.testDispatcherProvider

    private val canPromoteImportPasswordsToggle: Toggle = mock()
    private val autofillSelfToggle: Toggle = mock()
    private val canImportFromGooglePasswordManagerToggle: Toggle = mock()

    private val hasEverImportedPasswordsFlow = MutableSharedFlow<Boolean>(replay = 1)

    private lateinit var testee: RealImportInPasswordsVisibility

    @Before
    fun setUp() = runTest {
        setupAutofillFeatureToggles(
            canPromoteImportPasswords = true,
            autofillSelfEnabled = true,
            canImportFromGooglePasswordManager = true,
        )
        setupWebViewCapabilities(capable = true)
        setupAutofillStore(
            hasEverImportedPasswords = false,
            hasDismissedImportedPasswordsPromo = false,
        )

        whenever(internalAutofillStore.hasEverImportedPasswordsFlow()).thenReturn(hasEverImportedPasswordsFlow)
    }

    @Test
    fun whenAllConditionsMetThenCanShowImportPromo() = runTest {
        createTestee()
        assertTrue(testee.canShowImportInPasswords(5))
    }

    @Test
    fun whenCanPromoteImportPasswordsDisabledThenCannotShowImportPromo() = runTest {
        setupAutofillFeatureToggles(canPromoteImportPasswords = false)
        createTestee()

        assertFalse(testee.canShowImportInPasswords(5))
    }

    @Test
    fun whenUserHasEverImportedPasswordsThenCannotShowImportPromo() = runTest {
        setupAutofillStore(hasEverImportedPasswords = true)
        createTestee()

        assertFalse(testee.canShowImportInPasswords(5))
    }

    @Test
    fun whenUserHasDismissedImportedPasswordsPromoThenCannotShowImportPromo() = runTest {
        setupAutofillStore(hasDismissedImportedPasswordsPromo = true)
        createTestee()

        assertFalse(testee.canShowImportInPasswords(5))
    }

    @Test
    fun whenAutofillSelfDisabledThenCannotShowImportPromo() = runTest {
        setupAutofillFeatureToggles(autofillSelfEnabled = false)
        createTestee()

        assertFalse(testee.canShowImportInPasswords(5))
    }

    @Test
    fun whenCanImportFromGooglePasswordManagerDisabledThenCannotShowImportPromo() = runTest {
        setupAutofillFeatureToggles(canImportFromGooglePasswordManager = false)
        createTestee()

        assertFalse(testee.canShowImportInPasswords(5))
    }

    @Test
    fun whenWebViewImportingNotSupportedThenCannotShowImportPromo() = runTest {
        setupWebViewCapabilities(capable = false)
        createTestee()

        assertFalse(testee.canShowImportInPasswords(5))
    }

    @Test
    fun whenPasswordCountBelowMinimumThenCannotShowImportPromo() = runTest {
        createTestee()

        assertFalse(testee.canShowImportInPasswords(0))
    }

    @Test
    fun whenPasswordCountAtMaximumThenCanShowImportPromo() = runTest {
        createTestee()

        assertTrue(testee.canShowImportInPasswords(25))
    }

    @Test
    fun whenPasswordCountAboveMaximumThenCannotShowImportPromo() = runTest {
        createTestee()

        assertFalse(testee.canShowImportInPasswords(26))
    }

    @Test
    fun whenPromoDismissedThenCannotShowImportPromoAndFlagIsSet() = runTest {
        createTestee()
        assertTrue(testee.canShowImportInPasswords(5))

        testee.onPromoDismissed()

        assertFalse(testee.canShowImportInPasswords(5))
        verify(internalAutofillStore).hasDeclinedPasswordManagementImportPromo = true
    }

    @Test
    fun whenHasEverImportedPasswordsChangesToTrueThenCannotShowImportPromo() = runTest {
        createTestee()

        // Initially can show promo
        assertTrue(testee.canShowImportInPasswords(5))

        // Simulate user importing passwords
        hasEverImportedPasswordsFlow.emit(true)

        // Now cannot show promo
        assertFalse(testee.canShowImportInPasswords(5))
    }

    @Test
    fun whenInitialEvaluationReturnsFalseThenDoesNotObserveFlow() = runTest {
        setupAutofillFeatureToggles(canPromoteImportPasswords = false)
        createTestee()

        // Emit true to the flow - should not affect the result since we're not observing
        hasEverImportedPasswordsFlow.emit(true)

        // Should still return false (not due to flow observation)
        assertFalse(testee.canShowImportInPasswords(5))
    }

    @Test
    fun whenInitialEvaluationReturnsTrueThenObservesFlowForChanges() = runTest {
        createTestee()
        assertTrue(testee.canShowImportInPasswords(5))

        // Test that the flow is being observed by emitting false first (should not change anything)
        hasEverImportedPasswordsFlow.emit(false)
        assertTrue(testee.canShowImportInPasswords(5))

        // Then emit true (should hide promo)
        hasEverImportedPasswordsFlow.emit(true)
        assertFalse(testee.canShowImportInPasswords(5))
    }

    private fun createTestee() {
        testee = RealImportInPasswordsVisibility(
            internalAutofillStore = internalAutofillStore,
            autofillFeature = autofillFeature,
            importGooglePasswordsCapabilityChecker = importPasswordCapabilityChecker,
            appCoroutineScope = appCoroutineScope,
            dispatcherProvider = dispatcherProvider,
        )
    }

    private fun setupAutofillFeatureToggles(
        canPromoteImportPasswords: Boolean = true,
        autofillSelfEnabled: Boolean = true,
        canImportFromGooglePasswordManager: Boolean = true,
    ) {
        whenever(canPromoteImportPasswordsToggle.isEnabled()).thenReturn(canPromoteImportPasswords)
        whenever(autofillSelfToggle.isEnabled()).thenReturn(autofillSelfEnabled)
        whenever(canImportFromGooglePasswordManagerToggle.isEnabled()).thenReturn(canImportFromGooglePasswordManager)

        whenever(autofillFeature.canPromoteImportPasswordsInPasswordManagement()).thenReturn(canPromoteImportPasswordsToggle)
        whenever(autofillFeature.self()).thenReturn(autofillSelfToggle)
        whenever(autofillFeature.canImportFromGooglePasswordManager()).thenReturn(canImportFromGooglePasswordManagerToggle)
    }

    private suspend fun setupWebViewCapabilities(capable: Boolean = true) {
        whenever(importPasswordCapabilityChecker.webViewCapableOfImporting()).thenReturn(capable)
    }

    private fun setupAutofillStore(
        hasEverImportedPasswords: Boolean = false,
        hasDismissedImportedPasswordsPromo: Boolean = false,
    ) {
        whenever(internalAutofillStore.hasEverImportedPasswords).thenReturn(hasEverImportedPasswords)
        whenever(internalAutofillStore.hasDeclinedPasswordManagementImportPromo).thenReturn(hasDismissedImportedPasswordsPromo)
    }
}
