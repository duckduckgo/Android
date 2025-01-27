package com.duckduckgo.autofill.impl.partialsave

import android.annotation.SuppressLint
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.partialsave.UsernameBackFiller.BackFillResult
import com.duckduckgo.autofill.impl.partialsave.UsernameBackFiller.BackFillResult.BackFillSupported
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UsernameBackFillerImplTest {

    private val partialCredentialSaveStore: PartialCredentialSaveStore = mock()
    private val feature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    private val testee = UsernameBackFillerImpl(
        autofillFeature = feature,
        partialCredentialSaveStore = partialCredentialSaveStore,
    )

    @Before
    fun setup() {
        configureAutofillFeatureState(enabled = true)
    }

    @Test
    fun whenUsernameFromJsIsPopulatedThenBackFillingIsNotSupported() = runTest {
        configureBackFillingToBeSupportedForUsername(USERNAME_FROM_BACKFILL)
        val result = testee.isBackFillingUsernameSupported(USERNAME_FROM_JAVASCRIPT, A_URL)
        result.verifyUsernameIsNotFromBackFill()
    }

    @Test
    fun whenUsernameFromJsIsEmptyStringThenBackFillingIsSupported() = runTest {
        configureBackFillingToBeSupportedForUsername(USERNAME_FROM_BACKFILL)
        val result = testee.isBackFillingUsernameSupported(usernameFromJavascript = "", A_URL)
        result.verifyUsernameIsFromBackFill()
    }

    @Test
    fun whenUsernameFromJsIsNullThenBackFillingIsSupported() = runTest {
        configureBackFillingToBeSupportedForUsername(USERNAME_FROM_BACKFILL)
        val result = testee.isBackFillingUsernameSupported(usernameFromJavascript = null, A_URL)
        result.verifyUsernameIsFromBackFill()
    }

    @Test
    fun whenBackFillingOtherwisePossibleButFeatureFlagDisabledThenBackFillingIsNotSupported() = runTest {
        configureAutofillFeatureState(enabled = false)
        configureBackFillingToBeSupportedForUsername(USERNAME_FROM_BACKFILL)
        val result = testee.isBackFillingUsernameSupported(usernameFromJavascript = null, A_URL)
        result.verifyUsernameIsNotFromBackFill()
    }

    @Test
    fun whenNoUsernameFromJavascriptButNoPartialSaveAvailableThenBackFillingIsNotSupported() = runTest {
        val result = testee.isBackFillingUsernameSupported(usernameFromJavascript = null, A_URL)
        result.verifyUsernameIsNotFromBackFill()
    }

    private fun BackFillResult.verifyUsernameIsNotFromBackFill() {
        assertTrue(this is BackFillResult.BackFillNotSupported)
    }

    private fun BackFillResult.verifyUsernameIsFromBackFill() {
        assertEquals(USERNAME_FROM_BACKFILL, (this as BackFillSupported).username)
    }

    @SuppressLint("DenyListedApi")
    private fun configureAutofillFeatureState(enabled: Boolean) {
        feature.partialFormSaves().setRawStoredState(Toggle.State(enable = enabled))
    }

    private suspend fun configureBackFillingToBeSupportedForUsername(username: String) {
        whenever(partialCredentialSaveStore.getUsernameForBackFilling(anyOrNull())).thenReturn(username)
    }

    companion object {
        private const val USERNAME_FROM_BACKFILL = "username-from-backfill"
        private const val USERNAME_FROM_JAVASCRIPT = "username-from-javascript"
        private const val A_URL = "https://example.com"
    }
}
