package com.duckduckgo.malicioussiteprotection.impl

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProtectionUpdateMigrationTest {
    @Test
    fun legacyWorkIsCancelledAndRepeatedUpdatesKeepTheReplacement() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val manager = WorkManager.getInstance(context)
        val legacy = "MALICIOUS_SITE_PROTECTION_HASH_PREFIXES_UPDATE_WORKER_TAG"
        val replacement = "${legacy}_v2"
        val old = PeriodicWorkRequestBuilder<MaliciousSiteProtectionHashPrefixesUpdateWorker>(480, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(legacy, ExistingPeriodicWorkPolicy.KEEP, old).result.get()
        val feature = mock<MaliciousSiteProtectionRCFeature>()
        whenever(feature.isFeatureEnabled()).thenReturn(true)
        whenever(feature.canUpdateDatasets()).thenReturn(true)
        whenever(feature.getHashPrefixUpdateFrequency()).thenReturn(480)
        val preferences = context.getSharedPreferences("protection_update_schedule", Context.MODE_PRIVATE)
        preferences.edit().putInt(legacy, 25_000).commit()
        val scheduler = MaliciousSiteProtectionHashPrefixesUpdateWorkerScheduler(manager, feature, ProtectionUpdateSchedule(context))

        scheduler.onPrivacyConfigDownloaded()
        val first = manager.getWorkInfosForUniqueWork(replacement).get().single()
        scheduler.onPrivacyConfigDownloaded()
        val second = manager.getWorkInfosForUniqueWork(replacement).get().single()

        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(old.id).get()!!.state)
        assertEquals(first.id, second.id)
        assertEquals(first.nextScheduleTimeMillis, second.nextScheduleTimeMillis)
        assertEquals(25_000, preferences.getInt(legacy, -1))
        assertTrue(second.state == WorkInfo.State.ENQUEUED || second.state == WorkInfo.State.RUNNING)

        whenever(feature.canUpdateDatasets()).thenReturn(false)
        scheduler.onPrivacyConfigDownloaded()
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfosForUniqueWork(replacement).get().single().state)
        assertFalse(preferences.contains(legacy))
        whenever(feature.canUpdateDatasets()).thenReturn(true)
        scheduler.onPrivacyConfigDownloaded()
        val reenabled = manager.getWorkInfosForUniqueWork(replacement).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, reenabled.state)
        assertTrue(preferences.getInt(legacy, -1) in 0..50_000)
    }
}
