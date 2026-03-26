package com.duckduckgo.app.browser.indonesiamessage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.indonesiamessage.RealIndonesiaNewTabSectionDataStore.Companion.INTERVAL_HOURS
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RealIndonesiaNewTabSectionDataStoreTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("indonesia_new_tab_section_store") },
        )

    private val testee: IndonesiaNewTabSectionDataStore = RealIndonesiaNewTabSectionDataStore(testDataStore)

    @Test
    fun whenNotUpdatedThenDefaultValueForShowMessageIsFalse() = runTest {
        assertFalse(testee.showMessage.first())
    }

    @Test
    fun whenUpdateShowMessageCalledOnceWithMaxCountZeroThenShowMessageIsFalse() = runTest {
        testee.updateShowMessage(0)
        assertFalse(testee.showMessage.first())
    }

    @Test
    fun whenUpdateShowMessageCalledOnceWithMaxCountOneThenShowMessageIsTrue() = runTest {
        testee.updateShowMessage(1)
        assertTrue(testee.showMessage.first())
    }

    @Test
    fun whenUpdateShowMessageCalledMultipleTimesInTheSameDayWithMaxCountOneThenShowMessageIsTrue() = runTest {
        testee.updateShowMessage(1)
        assertTrue(testee.showMessage.first())

        testee.updateShowMessage(1)
        assertTrue(testee.showMessage.first())

        testee.updateShowMessage(1)
        assertTrue(testee.showMessage.first())
    }

    @Test
    fun whenUpdateShowMessageCalledMultipleTimesInDifferentDaysWithMaxCountTwoThenShowMessageIsTrueOnlyInFirstTwoDays() = runTest {
        // Day one, message shown no matter how many times it is called
        testee.updateShowMessage(2)
        assertTrue(testee.showMessage.first())

        testee.updateShowMessage(2)
        assertTrue(testee.showMessage.first())

        testee.updateShowMessage(2)
        assertTrue(testee.showMessage.first())

        testee.updateShowMessage(2)
        assertTrue(testee.showMessage.first())

        testee.updateShowMessage(2)
        assertTrue(testee.showMessage.first())

        // Day two, message shown no matter how many times it is called
        val mockTimeDayTwo = Instant.now().toEpochMilli() + TimeUnit.HOURS.toMillis(INTERVAL_HOURS)
        mockConstruction(Instant::class.java) { mock, _ ->
            whenever(mock.toEpochMilli()).thenReturn(mockTimeDayTwo)
        }.use {
            testee.updateShowMessage(2)
            assertTrue(testee.showMessage.first())

            testee.updateShowMessage(2)
            assertTrue(testee.showMessage.first())

            testee.updateShowMessage(2)
            assertTrue(testee.showMessage.first())

            testee.updateShowMessage(2)
            assertTrue(testee.showMessage.first())
        }

        // Day three, message not shown
        val mockTimeDayThree = mockTimeDayTwo + TimeUnit.HOURS.toMillis(INTERVAL_HOURS)
        mockConstruction(Instant::class.java) { mock, _ ->
            whenever(mock.toEpochMilli()).thenReturn(mockTimeDayThree)
        }.use {
            testee.updateShowMessage(2)
            assertFalse(testee.showMessage.first())

            testee.updateShowMessage(2)
            assertFalse(testee.showMessage.first())

            testee.updateShowMessage(2)
            assertFalse(testee.showMessage.first())

            testee.updateShowMessage(2)
            assertFalse(testee.showMessage.first())
        }
    }

    @Test
    fun whenUpdateShowMessageCalledAfterMessageDismissedThenShowMessageIsFalse() = runTest {
        testee.updateShowMessage(1)
        assertTrue(testee.showMessage.first())

        testee.dismissMessage()

        testee.updateShowMessage(1)
        assertFalse(testee.showMessage.first())
    }

    @Test
    fun whenUpdateShowMessageCalledTheNextDayAfterMessageDismissedThenShowMessageIsTrue() = runTest {
        testee.updateShowMessage(2)
        assertTrue(testee.showMessage.first())

        // Day one, message dismissed
        testee.dismissMessage()

        // Day one, message not shown after it was dismissed
        testee.updateShowMessage(2)
        assertFalse(testee.showMessage.first())

        // Day two, message shown even if it was dismissed the day before
        val mockTimeDayTwo = Instant.now().toEpochMilli() + TimeUnit.HOURS.toMillis(INTERVAL_HOURS)
        mockConstruction(Instant::class.java) { mock, _ ->
            whenever(mock.toEpochMilli()).thenReturn(mockTimeDayTwo)
        }.use {
            testee.updateShowMessage(2)
            assertTrue(testee.showMessage.first())
        }
    }
}
