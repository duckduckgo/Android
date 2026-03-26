package com.duckduckgo.malicioussiteprotection.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result.retry
import androidx.work.ListenableWorker.Result.success
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.MALWARE
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.PHISHING
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.SCAM
import com.duckduckgo.malicioussiteprotection.impl.data.MaliciousSiteRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MaliciousSiteProtectionFiltersUpdateWorkerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val maliciousSiteRepository: MaliciousSiteRepository = mock()
    private val dispatcherProvider = coroutineRule.testDispatcherProvider
    private val maliciousSiteProtectionFeature: MaliciousSiteProtectionRCFeature = mock()
    private val worker = TestListenableWorkerBuilder<MaliciousSiteProtectionFiltersUpdateWorker>(context = context)
        .setInputData(
            Data.Builder()
                .putStringArray("type", arrayOf(PHISHING.name, MALWARE.name))
                .build(),
        ).build()

    private val scamWorker = TestListenableWorkerBuilder<MaliciousSiteProtectionFiltersUpdateWorker>(context = context)
        .setInputData(
            Data.Builder()
                .putStringArray("type", arrayOf(SCAM.name))
                .build(),
        ).build()

    @Before
    fun setup() {
        worker.maliciousSiteRepository = maliciousSiteRepository
        scamWorker.maliciousSiteRepository = maliciousSiteRepository
        worker.dispatcherProvider = dispatcherProvider
        scamWorker.dispatcherProvider = dispatcherProvider
        worker.maliciousSiteProtectionFeature = maliciousSiteProtectionFeature
        scamWorker.maliciousSiteProtectionFeature = maliciousSiteProtectionFeature
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(true)
    }

    @Test
    fun doWork_returnsSuccessWhenFeatureIsDisabled() = runTest {
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(false)

        val result = worker.doWork()

        verify(maliciousSiteRepository, never()).loadFilters(any())
        assertEquals(success(), result)
    }

    @Test
    fun doWork_returnsSuccessWhenCanUpdateDatasetsIsDisabled() = runTest {
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(false)

        val result = worker.doWork()

        verify(maliciousSiteRepository, never()).loadFilters(any())
        assertEquals(success(), result)
    }

    @Test
    fun doWork_returnsSuccessWhenLoadFiltersSucceeds() = runTest {
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)

        val result = worker.doWork()

        assertEquals(success(), result)
        verify(maliciousSiteRepository).loadFilters(PHISHING, MALWARE)
    }

    @Test
    fun doWork_returnsRetryWhenLoadFiltersFails() = runTest {
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(maliciousSiteRepository.loadFilters(PHISHING, MALWARE)).thenReturn(Result.failure(Exception()))

        val result = worker.doWork()

        assertEquals(retry(), result)
    }

    @Test
    fun doWork_returnsSuccessWhenLoadFiltersWithScamAndRCFlagDisabled() = runTest {
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.scamProtectionEnabled()).thenReturn(false)

        val result = scamWorker.doWork()

        assertEquals(success(), result)
        verify(maliciousSiteRepository, never()).loadFilters(SCAM)
    }
}

class MaliciousSiteProtectionFiltersUpdateWorkerSchedulerTest {

    private val workManager: WorkManager = mock()
    private val maliciousSiteProtectionFeature: MaliciousSiteProtectionRCFeature = mock()
    private val scheduler = MaliciousSiteProtectionFiltersUpdateWorkerScheduler(workManager, maliciousSiteProtectionFeature)

    @Test
    fun onPrivacyConfigDownloadedWithFeatureOnAndScamOff_schedulesPhishingAndMalwareWorkerAndCancelsScamWorker() {
        val updateFrequencyMinutes = 15L

        whenever(maliciousSiteProtectionFeature.getFilterSetUpdateFrequency()).thenReturn(updateFrequencyMinutes)
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.scamProtectionEnabled()).thenReturn(false)

        scheduler.onPrivacyConfigDownloaded()

        val workRequestCaptor = ArgumentCaptor.forClass(PeriodicWorkRequest::class.java)
        verify(workManager).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_TAG"),
            eq(ExistingPeriodicWorkPolicy.UPDATE),
            capture(workRequestCaptor),
        )

        verify(workManager).cancelUniqueWork(
            eq("MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG"),
        )
        verify(workManager, never()).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG"),
            any(),
            any(),
        )

        val capturedWorkRequest = workRequestCaptor.value
        val repeatInterval = capturedWorkRequest.workSpec.intervalDuration
        val expectedInterval = TimeUnit.MINUTES.toMillis(updateFrequencyMinutes)

        assertEquals(expectedInterval, repeatInterval)
    }

    @Test
    fun onPrivacyConfigDownloadedWithFeatureOnAndScamOn_schedulesBothWorkersWithUpdateFrequencyFromRCFlagAndUpdatePolicy() {
        val updateFrequencyMinutes = 15L

        whenever(maliciousSiteProtectionFeature.getFilterSetUpdateFrequency()).thenReturn(updateFrequencyMinutes)
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.scamProtectionEnabled()).thenReturn(true)

        scheduler.onPrivacyConfigDownloaded()

        val workRequestCaptor = ArgumentCaptor.forClass(PeriodicWorkRequest::class.java)
        verify(workManager).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_TAG"),
            eq(ExistingPeriodicWorkPolicy.UPDATE),
            capture(workRequestCaptor),
        )
        var capturedWorkRequest = workRequestCaptor.value
        var repeatInterval = capturedWorkRequest.workSpec.intervalDuration
        val expectedInterval = TimeUnit.MINUTES.toMillis(updateFrequencyMinutes)
        var inputData = capturedWorkRequest.workSpec.input

        assertEquals(expectedInterval, repeatInterval)
        assertEquals(
            inputData.getStringArray("type")?.toList(),
            listOf(PHISHING.name, MALWARE.name),
        )

        val scamWorkRequestCaptor = ArgumentCaptor.forClass(PeriodicWorkRequest::class.java)
        verify(workManager).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG"),
            eq(ExistingPeriodicWorkPolicy.UPDATE),
            capture(scamWorkRequestCaptor),
        )

        capturedWorkRequest = scamWorkRequestCaptor.value
        repeatInterval = capturedWorkRequest.workSpec.intervalDuration
        inputData = capturedWorkRequest.workSpec.input

        assertEquals(expectedInterval, repeatInterval)
        assertEquals(
            inputData.getStringArray("type")?.toList(),
            listOf(SCAM.name),
        )
    }

    @Test
    fun onPrivacyConfigDownloadedWithCanUpdateDatasetOff_cancelsBothWorkers() {
        val updateFrequencyMinutes = 15L

        whenever(maliciousSiteProtectionFeature.getFilterSetUpdateFrequency()).thenReturn(updateFrequencyMinutes)
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(false)

        scheduler.onPrivacyConfigDownloaded()

        verify(workManager).cancelUniqueWork(
            eq("MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_TAG"),
        )
        verify(workManager).cancelUniqueWork(
            eq("MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG"),
        )
        verify(workManager, never()).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_TAG"),
            any(),
            any(),
        )
        verify(workManager, never()).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_FILTERS_UPDATE_WORKER_SCAM_TAG"),
            any(),
            any(),
        )
    }
}
