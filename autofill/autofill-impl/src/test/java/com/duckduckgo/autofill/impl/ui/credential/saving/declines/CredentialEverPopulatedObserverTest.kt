package com.duckduckgo.autofill.impl.ui.credential.saving.declines

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class CredentialEverPopulatedObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val autofillStore: AutofillStore = mock()
    private val credentialCountFlow = MutableSharedFlow<Int>()

    @Before
    fun setup() {
        runTest {
            whenever(autofillStore.monitorDeclineCounts).thenReturn(true)
            whenever(autofillStore.autofillAvailable).thenReturn(true)
            whenever(autofillStore.getCredentialCount()).thenReturn(credentialCountFlow)
        }

        declineCounter = AutofillDisablingDeclineCounter(
            autofillStore = autofillStore,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )

        testee = CredentialEverPopulatedObserver(
            declineCounter = declineCounter,
            autofillStore = autofillStore,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    private lateinit var declineCounter: AutofillDisablingDeclineCounter
    private lateinit var testee: CredentialEverPopulatedObserver

    @Test
    fun whenUpdatedCredentialCountIsAbove0ThenDeclineCounterDisabled() = runTest {
        testee.onCreate(mock<LifecycleOwner>())
        assertTrue(declineCounter.isActive())

        simulateFlowEmissionUpdatedCredentialCount(1, this)

        assertFalse(declineCounter.isActive())
    }

    @Test
    fun whenUpdatedCredentialCountIs0ThenDeclineCounterNotDisabled() = runTest {
        testee.onCreate(mock<LifecycleOwner>())
        assertTrue(declineCounter.isActive())

        simulateFlowEmissionUpdatedCredentialCount(credentialCount = 0, this)

        assertTrue(declineCounter.isActive())
    }

    private suspend fun simulateFlowEmissionUpdatedCredentialCount(
        credentialCount: Int,
        scope: TestScope,
    ) {
        // simulate emitting an updated credential count
        scope.launch {
            credentialCountFlow.emit(credentialCount)
        }.join()
    }
}
