package com.duckduckgo.networkprotection.impl.autoexclude

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludePrompt.Trigger.INCOMPATIBLE_APP_MANUALLY_EXCLUDED
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludePrompt.Trigger.NEW_INCOMPATIBLE_APP_FOUND
import com.duckduckgo.networkprotection.store.NetPManualExclusionListRepository
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealAutoExcludePromptTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var manualExclusionListRepository: NetPManualExclusionListRepository

    private lateinit var autoExcludePrompt: AutoExcludePrompt
    private lateinit var autoExcludeAppsRepository: FakeAutoExcludeAppsRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        autoExcludeAppsRepository = FakeAutoExcludeAppsRepository()
        autoExcludePrompt = RealAutoExcludePrompt(
            manualExclusionListRepository,
            autoExcludeAppsRepository,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenManualExclusionListContainAppsForAutoExcludeThenAppsForPromptShouldOnlyIncludeProtectedApps() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionList()).thenReturn(
            listOf(
                NetPManuallyExcludedApp("test1", true),
                NetPManuallyExcludedApp("test2", false),
            ),
        )

        autoExcludeAppsRepository.setAppsForAutoExcludePrompt(
            listOf(
                VpnIncompatibleApp("test1"),
                VpnIncompatibleApp("test2"),
            ),
        )

        val result = autoExcludePrompt.getAppsForPrompt(NEW_INCOMPATIBLE_APP_FOUND)

        assertTrue(result.contains(VpnIncompatibleApp("test1")))
        assertFalse(result.contains(VpnIncompatibleApp("test2")))
    }

    @Test
    fun whenManualExclusionListIsEmptyThenAppsForPromptShouldIncludeAllAppsForPrompt() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionList()).thenReturn(emptyList())
        autoExcludeAppsRepository.setAppsForAutoExcludePrompt(
            listOf(
                VpnIncompatibleApp("test1"),
                VpnIncompatibleApp("test2"),
            ),
        )

        val result = autoExcludePrompt.getAppsForPrompt(NEW_INCOMPATIBLE_APP_FOUND)

        assertTrue(result.contains(VpnIncompatibleApp("test1")))
        assertTrue(result.contains(VpnIncompatibleApp("test2")))
    }

    @Test
    fun whenNoAppsForPromptThenReturnEmpty() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionList()).thenReturn(emptyList())

        val result = autoExcludePrompt.getAppsForPrompt(NEW_INCOMPATIBLE_APP_FOUND)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenWithIncompatibleAppsAndNoManualExclusionThenReturnAllIncompatibleApps() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionList()).thenReturn(emptyList())
        autoExcludeAppsRepository.setIncompatibleApps(
            listOf(
                VpnIncompatibleApp("test1"),
                VpnIncompatibleApp("test2"),
            ),
        )

        val result = autoExcludePrompt.getAppsForPrompt(INCOMPATIBLE_APP_MANUALLY_EXCLUDED)

        assertTrue(result.contains(VpnIncompatibleApp("test1")))
        assertTrue(result.contains(VpnIncompatibleApp("test2")))
    }

    @Test
    fun whenWithIncompatibleAppsAndManualExclusionThenReturnAllProtectedIncompatibleApps() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionList()).thenReturn(
            listOf(
                NetPManuallyExcludedApp("test1", true),
                NetPManuallyExcludedApp("test2", false),
            ),
        )
        autoExcludeAppsRepository.setIncompatibleApps(
            listOf(
                VpnIncompatibleApp("test1"),
                VpnIncompatibleApp("test2"),
            ),
        )

        val result = autoExcludePrompt.getAppsForPrompt(INCOMPATIBLE_APP_MANUALLY_EXCLUDED)

        assertTrue(result.contains(VpnIncompatibleApp("test1")))
        assertFalse(result.contains(VpnIncompatibleApp("test2")))
    }

    @Test
    fun whenWithIncompatibleAppsAllManuallyExcludedThenReturnEmpty() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionList()).thenReturn(
            listOf(
                NetPManuallyExcludedApp("test1", false),
                NetPManuallyExcludedApp("test2", false),
            ),
        )
        autoExcludeAppsRepository.setIncompatibleApps(
            listOf(
                VpnIncompatibleApp("test1"),
                VpnIncompatibleApp("test2"),
            ),
        )

        val result = autoExcludePrompt.getAppsForPrompt(INCOMPATIBLE_APP_MANUALLY_EXCLUDED)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenNoIncompatibleAppsThenReturnEmpty() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionList()).thenReturn(
            listOf(
                NetPManuallyExcludedApp("test1", false),
                NetPManuallyExcludedApp("test2", false),
            ),
        )
        autoExcludeAppsRepository.setIncompatibleApps(emptyList())

        val result = autoExcludePrompt.getAppsForPrompt(INCOMPATIBLE_APP_MANUALLY_EXCLUDED)

        assertTrue(result.isEmpty())
    }
}
