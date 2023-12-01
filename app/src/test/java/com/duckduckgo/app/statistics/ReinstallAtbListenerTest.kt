package com.duckduckgo.app.statistics

import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.BackupDataStore
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class ReinstallAtbListenerTest {

    private lateinit var testee: ReinstallAtbListener

    private val mockStatisticsDataStore: StatisticsDataStore = mock()
    private val mockBackupDataStore: BackupDataStore = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun before() {
        whenever(mockStatisticsDataStore.hasInstallationStatistics).thenReturn(true)
        testee = ReinstallAtbListener(
            mockStatisticsDataStore,
            mockBackupDataStore,
        )
    }

    @Test
    fun givenStatisticsATBPersistedWhenAtbInitialiseCalledIfBackupATBNotExistThenChangeBackupATB() = runTest {
        whenever(mockStatisticsDataStore.atb).thenReturn(Atb("atb"))
        whenever(mockBackupDataStore.atb).thenReturn(null)

        testee.beforeAtbInit()

        verify(mockBackupDataStore).atb = mockStatisticsDataStore.atb
    }

    @Test
    fun givenStatisticsATBPersistedWhenAtbInitialiseCalledIfBackupATBIsDifferentThenChangeBackupATB() = runTest {
        whenever(mockStatisticsDataStore.atb).thenReturn(Atb("atb"))
        whenever(mockBackupDataStore.atb).thenReturn(Atb("oldAtb"))

        testee.beforeAtbInit()

        verify(mockBackupDataStore).atb = mockStatisticsDataStore.atb
    }

    @Test
    fun givenInstallWhenAtbInitialiseCalledIfBackupATBAndStatisticsATBAreDifferentThenBackupATBNotChanged() = runTest {
        whenever(mockStatisticsDataStore.atb).thenReturn(null)
        whenever(mockBackupDataStore.atb).thenReturn(Atb("oldAtb"))
        whenever(mockStatisticsDataStore.hasInstallationStatistics).thenReturn(false)

        testee.beforeAtbInit()

        verify(mockBackupDataStore).atb != mockStatisticsDataStore.atb
    }

    @Test
    fun givenReturningUserWhenInstallThenRemoveOldATB() = runTest {
        whenever(mockStatisticsDataStore.atb).thenReturn(null)
        whenever(mockBackupDataStore.atb).thenReturn(Atb("oldAtb"))
        whenever(mockStatisticsDataStore.hasInstallationStatistics).thenReturn(false)

        testee.beforeAtbInit()

        verify(mockBackupDataStore).atb = null
    }

    @Test
    fun givenReturningUserWhenInstallThenAssignReturningUserVariant() = runTest {
        whenever(mockStatisticsDataStore.atb).thenReturn(null)
        whenever(mockBackupDataStore.atb).thenReturn(Atb("oldAtb"))
        whenever(mockStatisticsDataStore.hasInstallationStatistics).thenReturn(false)

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = ReinstallAtbListener.REINSTALL_VARIANT
    }

    @Test
    fun givenNewUserWhenInstallThenDontAssignReturningUserVariant() = runTest {
        whenever(mockStatisticsDataStore.atb).thenReturn(null)
        whenever(mockBackupDataStore.atb).thenReturn(null)
        whenever(mockStatisticsDataStore.hasInstallationStatistics).thenReturn(false)

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore, never()).variant = ReinstallAtbListener.REINSTALL_VARIANT
    }
}
