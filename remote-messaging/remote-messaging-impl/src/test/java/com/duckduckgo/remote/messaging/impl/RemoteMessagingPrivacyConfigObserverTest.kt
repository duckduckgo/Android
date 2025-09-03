package com.duckduckgo.remote.messaging.impl

import android.annotation.SuppressLint
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@SuppressLint("DenyListedApi")
class RemoteMessagingPrivacyConfigObserverTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private var remoteMessagingFeatureToggles: RemoteMessagingFeatureToggles = FakeFeatureToggleFactory.create(
        RemoteMessagingFeatureToggles::class.java,
    )
    private val remoteMessagingConfigRepository = mock<RemoteMessagingConfigRepository>()
    private val workManager = mock<WorkManager>()

    private lateinit var testee: RemoteMessagingPrivacyConfigObserver

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        testee = RemoteMessagingPrivacyConfigObserver(
            appCoroutineScope = coroutinesTestRule.testScope,
            dispatcherProvider = coroutinesTestRule.testDispatcherProvider,
            remoteMessagingFeatureToggles = remoteMessagingFeatureToggles,
            remoteMessagingConfigRepository = remoteMessagingConfigRepository,
            workManager = workManager,
        )
    }

    @Test
    fun `Do not invalidate on privacy config downloaded if always process FF and invalidate RMF after privacy config Enabled`() = runTest {
        remoteMessagingFeatureToggles.alwaysProcessRemoteConfig().setRawStoredState(State(true))
        remoteMessagingFeatureToggles.invalidateRMFAfterPrivacyConfigDownloaded().setRawStoredState(State(true))

        testee.onPrivacyConfigDownloaded()

        verifyNoInteractions(remoteMessagingConfigRepository)
    }

    @Test
    fun `Do not invalidate on privacy config downloaded if always process FF and invalidate RMF after privacy config Disabled`() = runTest {
        remoteMessagingFeatureToggles.alwaysProcessRemoteConfig().setRawStoredState(State(false))
        remoteMessagingFeatureToggles.invalidateRMFAfterPrivacyConfigDownloaded().setRawStoredState(State(false))

        testee.onPrivacyConfigDownloaded()

        verifyNoInteractions(remoteMessagingConfigRepository)
    }

    @Test
    fun `invalidate on privacy config downloaded if always process FF Disabled and invalidate RMF after privacy config Enabled`() = runTest {
        remoteMessagingFeatureToggles.alwaysProcessRemoteConfig().setRawStoredState(State(false))
        remoteMessagingFeatureToggles.invalidateRMFAfterPrivacyConfigDownloaded().setRawStoredState(State(true))

        testee.onPrivacyConfigDownloaded()

        verify(remoteMessagingConfigRepository).invalidate()
    }

    @Test
    fun `Do not invalidate on privacy config downloaded if always process FF Enabled and invalidate RMF after privacy config Disabled`() = runTest {
        remoteMessagingFeatureToggles.alwaysProcessRemoteConfig().setRawStoredState(State(true))
        remoteMessagingFeatureToggles.invalidateRMFAfterPrivacyConfigDownloaded().setRawStoredState(State(false))

        testee.onPrivacyConfigDownloaded()

        verifyNoInteractions(remoteMessagingConfigRepository)
    }

    @Test
    fun `Schedule work if canScheduleOnPrivacyConfigUpdates is enabled`() = runTest {
        remoteMessagingFeatureToggles.canScheduleOnPrivacyConfigUpdates().setRawStoredState(State(true))

        testee.onPrivacyConfigDownloaded()

        verify(workManager).enqueueUniquePeriodicWork(
            eq(REMOTE_MESSAGING_DOWNLOADER_WORKER_TAG),
            eq(ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE),
            any(),
        )
    }
}
