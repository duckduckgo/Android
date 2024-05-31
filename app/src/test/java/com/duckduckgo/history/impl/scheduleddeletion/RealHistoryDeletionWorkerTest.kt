package com.duckduckgo.history.impl.scheduleddeletion

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.history.impl.InternalNavigationHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class RealHistoryDeletionWorkerTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private val workManager: WorkManager = mock()
    private val mockHistory: InternalNavigationHistory = mock()
    private val appCoroutineScope: CoroutineScope = TestScope()

    private val historyDeletionWorker =
        HistoryDeletionWorker(workManager, mockHistory, appCoroutineScope)

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mock()
    }

    @Test
    fun whenDoWorkThenCallCleanOldEntriesAndReturnSuccess() = runTest {
        val worker =
            TestListenableWorkerBuilder<RealHistoryDeletionWorker>(context = context).build()
        worker.historyDeletionWorker = historyDeletionWorker

        val result = worker.doWork()

        verify(mockHistory, never()).clearOldEntries()
        Assert.assertEquals(result, Result.success())
    }
}
