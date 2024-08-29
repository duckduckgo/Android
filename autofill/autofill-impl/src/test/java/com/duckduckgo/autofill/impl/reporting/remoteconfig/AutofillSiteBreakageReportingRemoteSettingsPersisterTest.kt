package com.duckduckgo.autofill.impl.reporting.remoteconfig

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.reporting.AutofillSiteBreakageReportingDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class AutofillSiteBreakageReportingRemoteSettingsPersisterTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val dataStore: AutofillSiteBreakageReportingDataStore = mock()

    private val testee = AutofillSiteBreakageReportingRemoteSettingsPersister(
        dataStore = dataStore,
        appCoroutineScope = coroutineTestRule.testScope,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenSettingsIsEmptyStringThenNothingStored() = runTest {
        testee.store("")
        verify(dataStore, never()).updateMinimumNumberOfDaysBeforeReportPromptReshown(any())
    }

    @Test
    fun whenSettingsIsEmptyThenNothingStored() = runTest {
        testee.store("{}")
        verify(dataStore, never()).updateMinimumNumberOfDaysBeforeReportPromptReshown(any())
    }

    @Test
    fun whenSettingsIsSpecificThenThatValueIsStored() = runTest {
        testee.store(validJson(10))
        verify(dataStore).updateMinimumNumberOfDaysBeforeReportPromptReshown(10)
    }

    @Suppress("SameParameterValue")
    private fun validJson(numberDays: Int): String {
        return """
            {"monitorIntervalDays": $numberDays}
        """.trimIndent()
    }
}
