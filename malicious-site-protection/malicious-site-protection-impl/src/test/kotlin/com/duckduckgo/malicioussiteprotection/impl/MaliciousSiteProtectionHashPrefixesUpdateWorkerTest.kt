package com.duckduckgo.malicioussiteprotection.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result.retry
import androidx.work.ListenableWorker.Result.success
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.malicioussiteprotection.impl.data.MaliciousSiteRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class MaliciousSiteProtectionHashPrefixesUpdateWorkerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val maliciousSiteRepository: MaliciousSiteRepository = mock()
    private val dispatcherProvider = coroutineRule.testDispatcherProvider
    private val maliciousSiteProtectionFeature: MaliciousSiteProtectionRCFeature = mock()
    private val worker = TestListenableWorkerBuilder<MaliciousSiteProtectionHashPrefixesUpdateWorker>(context = context).build()

    @Before
    fun setup() {
        worker.maliciousSiteRepository = maliciousSiteRepository
        worker.dispatcherProvider = dispatcherProvider
        worker.maliciousSiteProtectionFeature = maliciousSiteProtectionFeature
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(true)
    }

    @Test
    fun doWork_returnsSuccessWhenFeatureIsDisabled() = runTest {
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(false)

        val result = worker.doWork()

        verify(maliciousSiteRepository, never()).loadHashPrefixes()
        assertEquals(success(), result)
    }

    @Test
    fun doWork_returnsSuccessWhenUpdateDatasetsIsDisabled() = runTest {
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(false)

        val result = worker.doWork()

        verify(maliciousSiteRepository, never()).loadHashPrefixes()
        assertEquals(success(), result)
    }

    @Test
    fun doWork_returnsSuccessWhenLoadHashPrefixesSucceeds() = runTest {
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)

        val result = worker.doWork()

        assertEquals(success(), result)
        verify(maliciousSiteRepository).loadHashPrefixes()
    }

    @Test
    fun doWork_returnsRetryWhenLoadHashPrefixesFails() = runTest {
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(maliciousSiteRepository.loadHashPrefixes()).thenReturn(Result.failure(Exception()))

        val result = worker.doWork()

        assertEquals(retry(), result)
    }
}

class MaliciousSiteProtectionHashPrefixesUpdateWorkerSchedulerTest {

    private val workManager: WorkManager = mock()
    private val maliciousSiteProtectionFeature: MaliciousSiteProtectionRCFeature = mock()
    private val scheduler = MaliciousSiteProtectionHashPrefixesUpdateWorkerScheduler(workManager, maliciousSiteProtectionFeature)

    @Test
    fun onPrivacyConfigDownloaded_schedulesWorkerWithUpdateFrequencyFromRCFlag() {
        val updateFrequencyMinutes = 15L

        whenever(maliciousSiteProtectionFeature.getHashPrefixUpdateFrequency()).thenReturn(updateFrequencyMinutes)
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(true)

        scheduler.onPrivacyConfigDownloaded()

        val workRequestCaptor = ArgumentCaptor.forClass(PeriodicWorkRequest::class.java)
        verify(workManager).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_TAG"),
            eq(ExistingPeriodicWorkPolicy.UPDATE),
            capture(workRequestCaptor),
        )

        val capturedWorkRequest = workRequestCaptor.value
        val repeatInterval = capturedWorkRequest.workSpec.intervalDuration
        val expectedInterval = TimeUnit.MINUTES.toMillis(updateFrequencyMinutes)

        assertEquals(expectedInterval, repeatInterval)
    }

    @Test
    fun onPrivacyConfigDownloadedWithCanUpdateDatasetOff_cancelsWorker() {
        val updateFrequencyMinutes = 15L

        whenever(maliciousSiteProtectionFeature.getFilterSetUpdateFrequency()).thenReturn(updateFrequencyMinutes)
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(false)

        scheduler.onPrivacyConfigDownloaded()

        verify(workManager).cancelUniqueWork(
            eq("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_TAG"),
        )
    }
}
