package com.duckduckgo.networkprotection.impl.autoexclude

import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludePrompt.Trigger.NEW_FLAGGED_APP
import com.duckduckgo.networkprotection.store.NetPExclusionListRepository
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealAutoExcludePromptTest {
    @Mock
    private lateinit var netPExclusionListRepository: NetPExclusionListRepository

    @Mock
    private lateinit var autoExcludeAppsRepository: AutoExcludeAppsRepository
    private lateinit var autoExcludePrompt: AutoExcludePrompt

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        autoExcludePrompt = RealAutoExcludePrompt(
            netPExclusionListRepository,
            autoExcludeAppsRepository,
        )
    }

    @Test
    fun whenManualExclusionListContainAppsForAutoExcludeThenAppsForPromptShouldOnlyIncludeProtectedApps() = runTest {
        whenever(netPExclusionListRepository.getManualAppExclusionList()).thenReturn(
            listOf(
                NetPManuallyExcludedApp("test1", true),
                NetPManuallyExcludedApp("test2", false),
            ),
        )

        whenever(autoExcludeAppsRepository.getAppsForAutoExcludePrompt()).thenReturn(
            listOf(
                VpnIncompatibleApp("test1"),
                VpnIncompatibleApp("test2"),
            ),
        )
        val result = autoExcludePrompt.getAppsForPrompt(NEW_FLAGGED_APP)

        assertTrue(result.contains(VpnIncompatibleApp("test1")))
        assertFalse(result.contains(VpnIncompatibleApp("test2")))
    }

    @Test
    fun whenManualExclusionListIsEmptyThenAppsForPromptShouldIncludeAllAppsForPrompt() = runTest {
        whenever(netPExclusionListRepository.getManualAppExclusionList()).thenReturn(emptyList())

        whenever(autoExcludeAppsRepository.getAppsForAutoExcludePrompt()).thenReturn(
            listOf(
                VpnIncompatibleApp("test1"),
                VpnIncompatibleApp("test2"),
            ),
        )
        val result = autoExcludePrompt.getAppsForPrompt(NEW_FLAGGED_APP)

        assertTrue(result.contains(VpnIncompatibleApp("test1")))
        assertTrue(result.contains(VpnIncompatibleApp("test2")))
    }

    @Test
    fun whenNoAppsForPromptThenReturnEmpty() = runTest {
        whenever(netPExclusionListRepository.getManualAppExclusionList()).thenReturn(emptyList())
        whenever(autoExcludeAppsRepository.getAppsForAutoExcludePrompt()).thenReturn(emptyList())

        val result = autoExcludePrompt.getAppsForPrompt(NEW_FLAGGED_APP)

        assertTrue(result.isEmpty())
    }
}
