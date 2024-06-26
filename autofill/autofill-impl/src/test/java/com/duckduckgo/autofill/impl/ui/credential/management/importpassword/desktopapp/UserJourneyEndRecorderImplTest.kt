package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_SUCCESSFUL
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_UNSUCCESSFUL
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class UserJourneyEndRecorderImplTest {

    private val pixel: Pixel = mock()
    private val dataStore: ImportPasswordsViaDesktopSyncDataStore = mock()

    private val testee = UserJourneyEndRecorderImpl(
        dataStore = dataStore,
        pixel = pixel,
    )

    @Test
    fun whenUserJourneyUnsuccessfulThenUserJourneyTimestampCleared() = runTest {
        testee.recordUnsuccessfulJourney()
        verify(dataStore).clearUserJourneyStartTime()
    }

    @Test
    fun whenUserJourneyUnsuccessfulThenPixelSent() = runTest {
        testee.recordUnsuccessfulJourney()
        verify(pixel).fire(AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_UNSUCCESSFUL)
    }

    @Test
    fun whenUserJourneySuccessfulThenPixelSent() = runTest {
        testee.recordSuccessfulJourney()
        verify(pixel).fire(AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_SUCCESSFUL)
    }
}
