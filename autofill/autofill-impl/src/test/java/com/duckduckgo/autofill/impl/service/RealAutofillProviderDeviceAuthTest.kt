package com.duckduckgo.autofill.impl.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealAutofillProviderDeviceAuthTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val timeProvider = FakeTimeProvider()

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineTestRule.testScope,
            produceFile = { context.preferencesDataStoreFile("autofill_device_auth_store") },
        )

    private val testee = RealAutofillProviderDeviceAuth(
        timeProvider = timeProvider,
        store = testDataStore,
    )

    @Test
    fun whenNoAuthenticationRegisteredThenAuthRequired() = runTest {
        assertTrue(testee.isAuthRequired())
    }

    @Test
    fun whenOldAuthenticationThenAuthRequired() = runTest {
        timeProvider.currentTime = 0L
        testee.recordSuccessfulAuthorization() // this will record 0L as lastAuthTime

        timeProvider.currentTime = System.currentTimeMillis() // moving time forward
        assertTrue(testee.isAuthRequired())
    }

    @Test
    fun whenRecentAuthenticationThenAuthNotRequired() = runTest {
        timeProvider.currentTime = System.currentTimeMillis()
        testee.recordSuccessfulAuthorization() // this will record 0L as lastAuthTime

        assertFalse(testee.isAuthRequired())
    }

    private class FakeTimeProvider : TimeProvider {
        var currentTime = 0L
        override fun currentTimeMillis(): Long = currentTime
    }
}
