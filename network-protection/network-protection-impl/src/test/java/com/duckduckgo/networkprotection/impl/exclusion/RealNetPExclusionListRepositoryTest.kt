package com.duckduckgo.networkprotection.impl.exclusion

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.networkprotection.impl.autoexclude.FakeAutoExcludeAppsRepository
import com.duckduckgo.networkprotection.impl.settings.FakeNetPSettingsLocalConfigFactory
import com.duckduckgo.networkprotection.store.NetPManualExclusionListRepository
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealNetPExclusionListRepositoryTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var manualExclusionListRepository: NetPManualExclusionListRepository

    @Mock
    private lateinit var packageManager: PackageManager

    @Mock
    private lateinit var context: Context

    private lateinit var repository: RealNetPExclusionListRepository
    private val autoExcludeAppsRepository = FakeAutoExcludeAppsRepository()
    private val localConfig = FakeNetPSettingsLocalConfigFactory.create()

    @Before
    @SuppressLint("DenyListedApi")
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository = RealNetPExclusionListRepository(
            manualExclusionListRepository,
            autoExcludeAppsRepository,
            localConfig,
            coroutineTestRule.testDispatcherProvider,
            packageManager,
            context,
        )
        whenever(packageManager.getInstalledApplications(PackageManager.GET_META_DATA)).thenReturn(
            listOf(
                newAppInfo("A App", "com.a.app"),
                newAppInfo("B App", "com.b.app"),
                newAppInfo("C App", "com.c.app"),
                newAppInfo("D App", "com.d.app"),
                newAppInfo("E App", "com.e.app"),
            ),
        )
    }

    @Test
    fun whenNoManualExcludedAppsAndAutoExcludeDisabledThenExcludedAppsIsEmpty() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionList()).thenReturn(emptyList())

        assertTrue(repository.getExcludedAppPackages().isEmpty())
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenAutoExcludeEnabledButNoIncompatibleAppsThenExcludedAppsIsEmpty() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionList()).thenReturn(emptyList())
        localConfig.autoExcludeBrokenApps().setRawStoredState(State(enable = true))

        assertTrue(repository.getExcludedAppPackages().isEmpty())
    }

    @Test
    fun whenAutoExcludeDisabledWithIncompatibleAppsThenExcludedAppsIsEmpty() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionList()).thenReturn(emptyList())
        autoExcludeAppsRepository.setIncompatibleApps(
            listOf(
                VpnIncompatibleApp("com.c.app"),
            ),
        )

        assertTrue(repository.getExcludedAppPackages().isEmpty())
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenAutoExcludeEnabledAndIncompatibleAppsInstalledThenExcludedAppsContainInstalledIncompatibleAppsOnly() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionList()).thenReturn(emptyList())
        localConfig.autoExcludeBrokenApps().setRawStoredState(State(enable = true))
        autoExcludeAppsRepository.setIncompatibleApps(
            listOf(
                VpnIncompatibleApp("com.c.app"),
                VpnIncompatibleApp("not.installed"),
            ),
        )

        assertEquals(
            listOf("com.c.app"),
            repository.getExcludedAppPackages(),
        )
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenManualExclusionIsNotEmptyAndIncompatibleAppsAvailableThenExcludedAppsReturnEligiblePackagesOnly() = runTest {
        whenever(manualExclusionListRepository.getManualAppExclusionList()).thenReturn(
            listOf(
                NetPManuallyExcludedApp(
                    packageId = "com.a.app", // Manually excluded and is in auto exclude
                    isProtected = false,
                ),
                NetPManuallyExcludedApp(
                    packageId = "com.b.app", // Manually included
                    isProtected = true,
                ),
                NetPManuallyExcludedApp(
                    packageId = "com.c.app", // Manually included and is in auto exclude
                    isProtected = true,
                ),
                NetPManuallyExcludedApp(
                    packageId = "com.d.app", // Manually excluded
                    isProtected = false,
                ),
            ),
        )
        localConfig.autoExcludeBrokenApps().setRawStoredState(State(enable = true))
        autoExcludeAppsRepository.setIncompatibleApps(
            listOf(
                VpnIncompatibleApp("com.a.app"), // Manually excluded too
                VpnIncompatibleApp("com.c.app"), // Will not be auto excluded since manually included
                VpnIncompatibleApp("com.e.app"), // Auto exclude ONLY
                VpnIncompatibleApp("not.installed"),
            ),
        )

        assertEquals(
            listOf(
                "com.a.app", // excluded via manual exclusion and auto exclude
                "com.d.app", // excluded via manual exclusion only
                "com.e.app", // excluded via auto exclude only
            ),
            repository.getExcludedAppPackages(),
        )
    }

    private fun newAppInfo(
        name: String,
        packageName: String,
    ): ApplicationInfo {
        return ApplicationInfo().apply {
            this.packageName = packageName
            this.name = name
        }
    }
}
