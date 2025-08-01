package com.duckduckgo.common.ui.internal.experiments.visual

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.FeatureName
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealVisualDesignExperimentConflictCheckerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var senseOfProtectionNewUserExperimentApr25Toggle: Toggle

    @Mock
    private lateinit var senseOfProtectionExistingUserExperimentApr25: Toggle

    @Mock
    private lateinit var senseOfProtectionNewUserExperimentMay25Toggle: Toggle

    @Mock
    private lateinit var senseOfProtectionExistingUserExperimentMay25: Toggle

    @Mock
    private lateinit var senseOfProtectionNewUserExperiment27May25: Toggle

    @Mock
    private lateinit var senseOfProtectionExistingUserExperiment27May25: Toggle

    @Mock
    private lateinit var unrelatedToggle: Toggle

    @Mock
    private lateinit var togglesInventory: FeatureTogglesInventory

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(senseOfProtectionNewUserExperimentApr25Toggle.featureName()).thenReturn(
            FeatureName("SenseOfProtectionToggles", "senseOfProtectionNewUserExperimentApr25"),
        )
        whenever(senseOfProtectionExistingUserExperimentApr25.featureName()).thenReturn(
            FeatureName("SenseOfProtectionToggles", "senseOfProtectionExistingUserExperimentApr25"),
        )
        whenever(senseOfProtectionNewUserExperimentMay25Toggle.featureName()).thenReturn(
            FeatureName("SenseOfProtectionToggles", "senseOfProtectionNewUserExperimentMay25"),
        )
        whenever(senseOfProtectionExistingUserExperimentMay25.featureName()).thenReturn(
            FeatureName("SenseOfProtectionToggles", "senseOfProtectionExistingUserExperimentMay25"),
        )
        whenever(senseOfProtectionNewUserExperiment27May25.featureName()).thenReturn(
            FeatureName("SenseOfProtectionToggles", "senseOfProtectionNewUserExperiment27May25"),
        )
        whenever(senseOfProtectionExistingUserExperiment27May25.featureName()).thenReturn(
            FeatureName("SenseOfProtectionToggles", "senseOfProtectionExistingUserExperiment27May25"),
        )
        whenever(unrelatedToggle.featureName()).thenReturn(
            FeatureName("unrelatedToggleParent", "unrelatedToggle"),
        )
    }

    @Test
    fun `no conflicting experiments, experiment enabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(emptyList())

        val testee = createTestee()

        testee.anyConflictingExperimentEnabled.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `senseOfProtectionNewUserExperimentApr25Toggle active, conflicting experiments return true`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(senseOfProtectionNewUserExperimentApr25Toggle))

        val testee = createTestee()

        testee.anyConflictingExperimentEnabled.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `senseOfProtectionNewUserExperimentMay25Toggle active, conflicting experiments return true`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(senseOfProtectionNewUserExperimentMay25Toggle))

        val testee = createTestee()

        testee.anyConflictingExperimentEnabled.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `senseOfProtectionExistingUserExperimentApr25 active, conflicting experiments return true`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(senseOfProtectionExistingUserExperimentApr25))

        val testee = createTestee()

        testee.anyConflictingExperimentEnabled.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `senseOfProtectionExistingUserExperimentMay25 active, conflicting experiments return true`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(senseOfProtectionExistingUserExperimentMay25))

        val testee = createTestee()

        testee.anyConflictingExperimentEnabled.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `senseOfProtectionNewUserExperiment27May25 active, conflicting experiments return true`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(senseOfProtectionNewUserExperiment27May25))

        val testee = createTestee()

        testee.anyConflictingExperimentEnabled.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `senseOfProtectionExistingUserExperiment27May25 active, conflicting experiments return true`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(senseOfProtectionExistingUserExperiment27May25))

        val testee = createTestee()

        testee.anyConflictingExperimentEnabled.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when unrelated experiment active, conflicting experiments return false`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(unrelatedToggle))

        val testee = createTestee()

        testee.anyConflictingExperimentEnabled.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createTestee(): RealVisualDesignExperimentConflictChecker {
        return RealVisualDesignExperimentConflictChecker(
            appCoroutineScope = coroutineRule.testScope,
            featureTogglesInventory = togglesInventory,
        )
    }
}
