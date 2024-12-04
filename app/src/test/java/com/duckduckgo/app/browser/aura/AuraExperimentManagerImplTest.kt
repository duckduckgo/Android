package com.duckduckgo.app.browser.aura

import com.duckduckgo.app.referral.AppReferrerDataStore
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.installation.api.installer.InstallSourceExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class AuraExperimentManagerImplTest {

    private val auraExperimentFeature: AuraExperimentFeature = mock()
    private val auraExperimentListJsonParser: AuraExperimentListJsonParser = mock()
    private val installSourceExtractor: InstallSourceExtractor = mock()
    private val statisticsDataStore: StatisticsDataStore = mock()
    private val appReferrerDataStore: AppReferrerDataStore = mock()
    private val toggle: Toggle = mock()

    private lateinit var testee: AuraExperimentManagerImpl

    @Before
    fun setup() {
        testee = AuraExperimentManagerImpl(
            auraExperimentFeature,
            auraExperimentListJsonParser,
            installSourceExtractor,
            statisticsDataStore,
            appReferrerDataStore,
        )
        whenever(auraExperimentFeature.self()).thenReturn(toggle)
    }

    @Test
    fun whenFeatureIsDisabledThenInitializeDoesNothing() = runTest {
        whenever(toggle.isEnabled()).thenReturn(false)

        testee.initialize()

        verifyNoInteractions(auraExperimentListJsonParser, installSourceExtractor, statisticsDataStore, appReferrerDataStore)
    }

    @Test
    fun whenFeatureIsEnabledAndInstallSourceIsNullThenInitializeDoesNothing() = runTest {
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(installSourceExtractor.extract()).thenReturn(null)

        testee.initialize()

        verifyNoInteractions(auraExperimentListJsonParser, statisticsDataStore, appReferrerDataStore)
    }

    @Test
    fun whenFeatureIsEnabledAndInstallSourceNotInPackagesThenInitializeDoesNothing() = runTest {
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(installSourceExtractor.extract()).thenReturn("x.y.z")
        whenever(toggle.getSettings()).thenReturn("json")
        whenever(auraExperimentListJsonParser.parseJson("json")).thenReturn(Packages(list = listOf("a.b.c")))

        testee.initialize()

        verifyNoInteractions(statisticsDataStore, appReferrerDataStore)
    }

    @Test
    fun whenFeatureIsEnabledAndInstallSourceInPackagesThenSetVariantAndOrigin() = runTest {
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(installSourceExtractor.extract()).thenReturn("a.b.c")
        whenever(toggle.getSettings()).thenReturn("json")
        whenever(auraExperimentListJsonParser.parseJson("json")).thenReturn(Packages(list = listOf("a.b.c")))

        testee.initialize()

        verify(statisticsDataStore).variant = AuraExperimentManagerImpl.VARIANT
        verify(appReferrerDataStore).utmOriginAttributeCampaign = AuraExperimentManagerImpl.ORIGIN
    }

    @Test
    fun whenReturningUserThenSetsReturningUserFlag() = runTest {
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(installSourceExtractor.extract()).thenReturn("a.b.c")
        whenever(toggle.getSettings()).thenReturn("json")
        whenever(auraExperimentListJsonParser.parseJson("json")).thenReturn(Packages(list = listOf("a.b.c")))
        whenever(statisticsDataStore.variant).thenReturn(AuraExperimentManagerImpl.RETURNING_USER)

        testee.initialize()

        verify(appReferrerDataStore).returningUser = true
    }
}
