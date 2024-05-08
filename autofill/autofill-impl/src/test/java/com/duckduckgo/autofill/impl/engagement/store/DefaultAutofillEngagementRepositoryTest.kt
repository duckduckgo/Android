package com.duckduckgo.autofill.impl.engagement.store

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENGAGEMENT_ACTIVE_USER
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.store.engagement.AutofillEngagementDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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

    @Before
    fun setup() {
        coroutineTestRule.testScope.runTest {
            whenever(autofillStore.getCredentialCount()).thenReturn(flowOf(0))
        }
    }

    private val testee = DefaultAutofillEngagementRepository(
        engagementDb = db,
        pixel = pixel,
        autofillStore = autofillStore,
        engagementBucketing = DefaultAutofillEngagementBucketing(),
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

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

    private fun AutofillPixelNames.verifySent() {
        assertTrue(pixel.firedPixels.contains(pixelName))
    }

    private fun AutofillPixelNames.verifyNotSent() {
        assertFalse(pixel.firedPixels.contains(pixelName))
    }

    private class FakePixel : Pixel {

        val firedPixels = mutableListOf<String>()

        override fun fire(
            pixel: PixelName,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
            type: PixelType,
        ) {
            firedPixels.add(pixel.pixelName)
        }

        override fun fire(
            pixelName: String,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
            type: PixelType,
        ) {
            firedPixels.add(pixelName)
        }

        override fun enqueueFire(
            pixel: PixelName,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
        ) {
            firedPixels.add(pixel.pixelName)
        }

        override fun enqueueFire(
            pixelName: String,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
        ) {
            firedPixels.add(pixelName)
        }
    }
}
