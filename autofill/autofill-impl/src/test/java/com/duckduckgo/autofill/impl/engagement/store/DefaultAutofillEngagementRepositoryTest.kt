package com.duckduckgo.autofill.impl.engagement.store

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.autofill.FakeAutofillServiceStore
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENGAGEMENT_ACTIVE_USER
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENGAGEMENT_ENABLED_USER
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_DISABLED_DAU
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_ENABLED_DAU
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_TOGGLED_OFF_SEARCH
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_TOGGLED_ON_SEARCH
import com.duckduckgo.autofill.impl.pixel.AutofillPixelParameters.LAST_USED_PIXEL_KEY
import com.duckduckgo.autofill.impl.securestorage.SecureStorage
import com.duckduckgo.autofill.impl.service.store.AutofillServiceStore
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.autofill.store.engagement.AutofillEngagementDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class DefaultAutofillEngagementRepositoryTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    val db = Room.inMemoryDatabaseBuilder(context, AutofillEngagementDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val pixel = FakePixel()
    private val autofillStore: InternalAutofillStore = mock()
    private val autofillServiceStore: AutofillServiceStore = FakeAutofillServiceStore()
    private val secureStorage: SecureStorage = mock()
    private val deviceAuthenticator: DeviceAuthenticator = mock()
    private val autofillPrefsStore: AutofillPrefsStore = mock()

    @Before
    fun setup() {
        coroutineTestRule.testScope.runTest {
            whenever(autofillStore.getCredentialCount()).thenReturn(flowOf(0))
            whenever(secureStorage.canAccessSecureStorage()).thenReturn(true)
            whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        }
    }

    private val testee = DefaultAutofillEngagementRepository(
        engagementDb = db,
        pixel = pixel,
        autofillStore = autofillStore,
        engagementBucketing = DefaultAutofillEngagementBucketing(),
        dispatchers = coroutineTestRule.testDispatcherProvider,
        secureStorage = secureStorage,
        deviceAuthenticator = deviceAuthenticator,
        autofillServiceStore = autofillServiceStore,
        autofillPrefsStore = autofillPrefsStore,
    )

    @Test
    fun whenAutofilledThenLastUsedDateIsUpdated() = runTest {
        testee.recordAutofilledToday()
        verify(autofillPrefsStore).dataLastAutofilledDate = eq(todayString())
    }

    @Test
    fun whenAutofilledButNotSearchedThenActiveUserPixelNotSent() = runTest {
        testee.recordAutofilledToday()
        AUTOFILL_ENGAGEMENT_ACTIVE_USER.verifyNotSent()
    }

    @Test
    fun whenSearchedButNotNotAutofilledThenActiveUserPixelNotSent() = runTest {
        testee.recordSearchedToday()
        AUTOFILL_ENGAGEMENT_ACTIVE_USER.verifyNotSent()
    }

    @Test
    fun whenSearchedAndAutofilledThenActiveUserPixelSent() = runTest {
        testee.recordSearchedToday()
        testee.recordAutofilledToday()
        AUTOFILL_ENGAGEMENT_ACTIVE_USER.verifySent()
    }

    @Test
    fun whenActiveUserPixelSentWithNoLastUsedThenOmittedFromParameters() = runTest {
        whenever(autofillPrefsStore.dataLastAutofilledDate).thenReturn(null)
        testee.recordSearchedToday()
        testee.recordAutofilledToday()

        val pixelParams = AUTOFILL_ENGAGEMENT_ACTIVE_USER.getParametersForFiredPixel()!!
        assertFalse(pixelParams.contains(LAST_USED_PIXEL_KEY))
    }

    @Test
    fun whenActiveUserPixelSentWithLastUsedThenIncludedInParameters() = runTest {
        val lastUsedDate = yesterdayString()
        whenever(autofillPrefsStore.dataLastAutofilledDate).thenReturn(lastUsedDate)

        testee.recordSearchedToday()
        testee.recordAutofilledToday()

        val pixelParams = AUTOFILL_ENGAGEMENT_ACTIVE_USER.getParametersForFiredPixel()!!
        assertEquals(lastUsedDate, pixelParams[LAST_USED_PIXEL_KEY])
    }

    @Test
    fun whenSearchedAutofillEnabledAndMoreThan9PasswordsThenEnabledUserPixelSent() = runTest {
        givenUserHasPasswords(10)
        givenUserHasAutofillEnabled(true)
        testee.recordAutofilledToday()
        testee.recordSearchedToday()
        AUTOFILL_ENGAGEMENT_ENABLED_USER.verifySent()
    }

    @Test
    fun whenSearchedAutofillDisabledAndMoreThan9PasswordsThenEnabledUserPixelNotSent() = runTest {
        givenUserHasPasswords(10)
        givenUserHasAutofillEnabled(false)
        testee.recordSearchedToday()
        testee.recordAutofilledToday()
        AUTOFILL_ENGAGEMENT_ENABLED_USER.verifyNotSent()
    }

    @Test
    fun whenSearchedAutofillEnabledAndLessThan10PasswordsThenEnabledUserPixelNotSent() = runTest {
        givenUserHasPasswords(9)
        givenUserHasAutofillEnabled(true)
        testee.recordSearchedToday()
        testee.recordAutofilledToday()
        AUTOFILL_ENGAGEMENT_ENABLED_USER.verifyNotSent()
    }

    @Test
    fun whenSearchedAndAutofillEnabledThenToggledPixelSent() = runTest {
        givenUserHasAutofillEnabled(true)
        testee.recordSearchedToday()
        AUTOFILL_TOGGLED_ON_SEARCH.verifySent()
    }

    @Test
    fun whenSearchedAndAutofillDisabledThenToggledOffPixelSent() = runTest {
        givenUserHasAutofillEnabled(false)
        testee.recordSearchedToday()
        AUTOFILL_TOGGLED_OFF_SEARCH.verifySent()
    }

    @Test
    fun whenSearchedAndAutofillServiceEnabledThenServiceEnabledPixelSent() = runTest {
        autofillServiceStore.updateDefaultAutofillProvider(true)
        testee.recordSearchedToday()
        AUTOFILL_SERVICE_ENABLED_DAU.verifySent()
    }

    @Test
    fun whenSearchedAndAutofillServiceDisabledThenServiceDisabledPixelSent() = runTest {
        autofillServiceStore.updateDefaultAutofillProvider(false)
        testee.recordSearchedToday()
        AUTOFILL_SERVICE_DISABLED_DAU.verifySent()
    }

    @Test
    fun whenSearchedWithNoDeviceAuthThenToggledPixelNotSent() = runTest {
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(false)
        testee.recordSearchedToday()
        AUTOFILL_TOGGLED_ON_SEARCH.verifyNotSent()
        AUTOFILL_TOGGLED_OFF_SEARCH.verifyNotSent()
    }

    @Test
    fun whenSearchedWithSecureStorageUnavailableThenToggledPixelNotSent() = runTest {
        whenever(secureStorage.canAccessSecureStorage()).thenReturn(false)
        testee.recordSearchedToday()
        AUTOFILL_TOGGLED_ON_SEARCH.verifyNotSent()
        AUTOFILL_TOGGLED_OFF_SEARCH.verifyNotSent()
    }

    private fun givenUserHasAutofillEnabled(autofillEnabled: Boolean) {
        whenever(autofillStore.autofillEnabled).thenReturn(autofillEnabled)
    }

    private suspend fun givenUserHasPasswords(storePasswords: Int) {
        whenever(autofillStore.getCredentialCount()).thenReturn(flowOf(storePasswords))
    }

    private fun AutofillPixelNames.verifySent() {
        assertTrue(pixel.firedPixels.contains(pixelName))
    }

    private fun AutofillPixelNames.verifyNotSent() {
        assertFalse(pixel.firedPixels.contains(pixelName))
    }

    private fun AutofillPixelNames.getParametersForFiredPixel(): Map<String, String>? = pixel.firedPixels[this.pixelName]

    private fun yesterdayString(): String {
        return DATE_FORMATTER.format(java.time.LocalDate.now().minusDays(1))
    }

    private fun todayString(): String {
        return DATE_FORMATTER.format(java.time.LocalDate.now())
    }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    private class FakePixel : Pixel {

        val firedPixels = mutableMapOf<String, Map<String, String>>()

        override fun fire(
            pixel: PixelName,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
            type: PixelType,
        ) {
            firedPixels[pixel.pixelName] = parameters
        }

        override fun fire(
            pixelName: String,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
            type: PixelType,
        ) {
            firedPixels[pixelName] = parameters
        }

        override fun enqueueFire(
            pixel: PixelName,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
        ) {
            firedPixels[pixel.pixelName] = parameters
        }

        override fun enqueueFire(
            pixelName: String,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
        ) {
            firedPixels[pixelName] = parameters
        }
    }
}
