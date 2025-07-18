package com.duckduckgo.autofill.impl.importing.settings

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.importing.InSettingsPasswordImportPromoRules
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class ImportGooglePasswordMainAppSettingsTest {

    private val promoRules: InSettingsPasswordImportPromoRules = mock()
    private val autofillStore: InternalAutofillStore = mock()
    private val pixel: Pixel = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val testee: ImportGooglePasswordMainAppSettings = ImportGooglePasswordMainAppSettings(
        pixel = pixel,
        autofillStore = autofillStore,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        promoRules = promoRules,
    )

    @Test
    fun whenPromoRulesIndicateItCanShowThenReturnTrue() = runTest {
        whenever(promoRules.canShowPromo()).thenReturn(true)
        assertTrue(testee.canShow())
    }

    @Test
    fun whenPromoRulesIndicateItCannotShowThenReturnFalse() = runTest {
        whenever(promoRules.canShowPromo()).thenReturn(false)
        assertFalse(testee.canShow())
    }
}
