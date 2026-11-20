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
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSiteDao
import com.duckduckgo.malicioussiteprotection.impl.data.db.RevisionEntity
import com.duckduckgo.malicioussiteprotection.impl.models.Type
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MaliciousSiteProtectionHashPrefixesUpdateWorkerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val maliciousSiteRepository: MaliciousSiteRepository = mock()
    private val dao: MaliciousSiteDao = mock()
    private val dispatcherProvider = coroutineRule.testDispatcherProvider
    private val maliciousSiteProtectionFeature: MaliciousSiteProtectionRCFeature = mock()
    private val worker = TestListenableWorkerBuilder<MaliciousSiteProtectionHashPrefixesUpdateWorker>(context = context)
        .setInputData(
            Data.Builder()
                .putStringArray("type", arrayOf(PHISHING.name, MALWARE.name))
                .build(),
        ).build()

    private val scamWorker = TestListenableWorkerBuilder<MaliciousSiteProtectionHashPrefixesUpdateWorker>(context = context)
        .setInputData(
            Data.Builder()
                .putStringArray("type", arrayOf(SCAM.name))
                .build(),
        ).build()

    @Before
    fun setup() {
        worker.maliciousSiteDao = dao
        scamWorker.maliciousSiteDao = dao
        worker.maliciousSiteRepository = maliciousSiteRepository
        scamWorker.maliciousSiteRepository = maliciousSiteRepository
        worker.dispatcherProvider = dispatcherProvider
        scamWorker.dispatcherProvider = dispatcherProvider
        worker.maliciousSiteProtectionFeature = maliciousSiteProtectionFeature
        scamWorker.maliciousSiteProtectionFeature = maliciousSiteProtectionFeature
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(true)
    }

    @Test
    fun bootstrapLoadsOnlyFeedsWithoutStoredRevision() = runTest {
        val bootstrap = TestListenableWorkerBuilder<MaliciousSiteProtectionHashPrefixesUpdateWorker>(context = context)
            .setInputData(Data.Builder().putStringArray("type", arrayOf(PHISHING.name, MALWARE.name)).putBoolean("bootstrap", true).build())
            .build()
        bootstrap.maliciousSiteRepository = maliciousSiteRepository
        bootstrap.maliciousSiteDao = dao
        bootstrap.dispatcherProvider = dispatcherProvider
        bootstrap.maliciousSiteProtectionFeature = maliciousSiteProtectionFeature
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(dao.getLatestRevision(PHISHING.name, Type.HASH_PREFIXES.name)).thenReturn(mock<RevisionEntity>())

        bootstrap.doWork()

        verify(maliciousSiteRepository).loadHashPrefixes(MALWARE)
    }

    @Test
    fun bootstrapSkipsNetworkWhenAllFeedsArePopulated() = runTest {
        val bootstrap = TestListenableWorkerBuilder<MaliciousSiteProtectionHashPrefixesUpdateWorker>(context = context)
            .setInputData(Data.Builder().putStringArray("type", arrayOf(PHISHING.name, MALWARE.name)).putBoolean("bootstrap", true).build())
            .build()
        bootstrap.maliciousSiteRepository = maliciousSiteRepository
        bootstrap.maliciousSiteDao = dao
        bootstrap.dispatcherProvider = dispatcherProvider
        bootstrap.maliciousSiteProtectionFeature = maliciousSiteProtectionFeature
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(dao.getLatestRevision(PHISHING.name, Type.HASH_PREFIXES.name)).thenReturn(mock<RevisionEntity>())
        whenever(dao.getLatestRevision(MALWARE.name, Type.HASH_PREFIXES.name)).thenReturn(mock<RevisionEntity>())

        assertEquals(success(), bootstrap.doWork())
        verify(maliciousSiteRepository, never()).loadHashPrefixes(any())
    }

    @Test
    fun doWork_returnsSuccessWhenFeatureIsDisabled() = runTest {
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(false)

        val result = worker.doWork()

        verify(maliciousSiteRepository, never()).loadHashPrefixes(any())
        assertEquals(success(), result)
    }

    @Test
    fun doWork_returnsSuccessWhenUpdateDatasetsIsDisabled() = runTest {
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(false)

        val result = worker.doWork()

        verify(maliciousSiteRepository, never()).loadHashPrefixes(any())
        assertEquals(success(), result)
    }

    @Test
    fun doWork_returnsSuccessWhenLoadHashPrefixesSucceeds() = runTest {
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)

        val result = worker.doWork()

        assertEquals(success(), result)
        verify(maliciousSiteRepository).loadHashPrefixes(PHISHING, MALWARE)
    }

    @Test
    fun doWork_returnsRetryWhenLoadHashPrefixesFails() = runTest {
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(maliciousSiteRepository.loadHashPrefixes(PHISHING, MALWARE)).thenReturn(Result.failure(Exception()))

        val result = worker.doWork()

        assertEquals(retry(), result)
    }

    @Test
    fun doWork_returnsSuccessWhenLoadHashPrefixesWithScamAndRCFlagDisabled() = runTest {
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.scamProtectionEnabled()).thenReturn(false)

        val result = scamWorker.doWork()

        assertEquals(success(), result)
        verify(maliciousSiteRepository, never()).loadHashPrefixes(SCAM)
    }
}

class MaliciousSiteProtectionHashPrefixesUpdateWorkerSchedulerTest {

    private val workManager: WorkManager = mock()
    private val maliciousSiteProtectionFeature: MaliciousSiteProtectionRCFeature = mock()
    private val schedule: ProtectionUpdateSchedule = mock()
    private val scheduler = MaliciousSiteProtectionHashPrefixesUpdateWorkerScheduler(workManager, maliciousSiteProtectionFeature, schedule)

    @Test
    fun onPrivacyConfigDownloadedWithFeatureOnAndScamOff_schedulesPhishingAndMalwareWorkerAndCancelsScamWorker() {
        val updateFrequencyMinutes = 15L

        whenever(maliciousSiteProtectionFeature.getHashPrefixUpdateFrequency()).thenReturn(updateFrequencyMinutes)
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.scamProtectionEnabled()).thenReturn(false)

        scheduler.onPrivacyConfigDownloaded()

        val workRequestCaptor = argumentCaptor<PeriodicWorkRequest>()
        verify(workManager).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_TAG_v2"),
            eq(ExistingPeriodicWorkPolicy.UPDATE),
            workRequestCaptor.capture(),
        )

        verify(workManager).cancelUniqueWork(
            eq("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_SCAM_TAG"),
        )
        verify(workManager, never()).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_SCAM_TAG_v2"),
            any(),
            any(),
        )

        val capturedWorkRequest = workRequestCaptor.firstValue
        val repeatInterval = capturedWorkRequest.workSpec.intervalDuration
        val expectedInterval = TimeUnit.MINUTES.toMillis(updateFrequencyMinutes)

        assertEquals(expectedInterval, repeatInterval)
    }

    @Test
    fun onPrivacyConfigDownloadedWithFeatureOnAndScamOn_schedulesBothWorkersWithUpdateFrequencyFromRCFlagAndUpdatePolicy() {
        val updateFrequencyMinutes = 15L

        whenever(maliciousSiteProtectionFeature.getHashPrefixUpdateFrequency()).thenReturn(updateFrequencyMinutes)
        whenever(maliciousSiteProtectionFeature.isFeatureEnabled()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.canUpdateDatasets()).thenReturn(true)
        whenever(maliciousSiteProtectionFeature.scamProtectionEnabled()).thenReturn(true)

        scheduler.onPrivacyConfigDownloaded()

        val workRequestCaptor = argumentCaptor<PeriodicWorkRequest>()
        verify(workManager).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_TAG_v2"),
            eq(ExistingPeriodicWorkPolicy.UPDATE),
            workRequestCaptor.capture(),
        )
        var capturedWorkRequest = workRequestCaptor.firstValue
        var repeatInterval = capturedWorkRequest.workSpec.intervalDuration
        val expectedInterval = TimeUnit.MINUTES.toMillis(updateFrequencyMinutes)
        var inputData = capturedWorkRequest.workSpec.input

        assertEquals(expectedInterval, repeatInterval)
        assertEquals(
            inputData.getStringArray("type")?.toList(),
            listOf(PHISHING.name, MALWARE.name),
        )

        val scamWorkRequestCaptor = argumentCaptor<PeriodicWorkRequest>()
        verify(workManager).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_SCAM_TAG_v2"),
            eq(ExistingPeriodicWorkPolicy.UPDATE),
            scamWorkRequestCaptor.capture(),
        )

        capturedWorkRequest = scamWorkRequestCaptor.firstValue
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

        verify(schedule).clear("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_TAG")
        verify(schedule).clear("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_SCAM_TAG")
        verify(workManager).cancelUniqueWork(
            eq("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_TAG"),
        )
        verify(workManager).cancelUniqueWork(
            eq("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_SCAM_TAG"),
        )
        verify(workManager, never()).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_TAG_v2"),
            any(),
            any(),
        )
        verify(workManager, never()).enqueueUniquePeriodicWork(
            eq("MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_SCAM_TAG_v2"),
            any(),
            any(),
        )
    }
}
