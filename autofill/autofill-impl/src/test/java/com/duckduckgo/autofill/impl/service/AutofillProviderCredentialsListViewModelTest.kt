package com.duckduckgo.autofill.impl.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.ui.credential.management.searching.CredentialListFilter
import com.duckduckgo.autofill.sync.CredentialsFixtures.spotifyCredentials
import com.duckduckgo.autofill.sync.CredentialsFixtures.twitterCredentials
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AutofillProviderCredentialsListViewModelTest {

    private val autofillStore: InternalAutofillStore = mock()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val testee = AutofillProviderCredentialsListViewModel(
        autofillStore = autofillStore,
        pixel = mock(),
        dispatchers = coroutineRule.testDispatcherProvider,
        credentialListFilter = FakeDomainListFilter(),
        appCoroutineScope = coroutineRule.testScope,
    )

    @Test
    fun whenOnViewCreatedShowAllCredentials() = runTest {
        whenever(autofillStore.getAllCredentials())
            .thenReturn(flowOf(listOf(twitterCredentials, spotifyCredentials)))

        testee.onViewCreated()

        testee.viewState.test {
            val awaitItem = awaitItem()
            assertEquals(2, awaitItem.logins?.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSearchQueryChangedThenFilterCredentials() = runTest {
        whenever(autofillStore.getAllCredentials())
            .thenReturn(flowOf(listOf(twitterCredentials, spotifyCredentials)))

        testee.onViewCreated()
        testee.onSearchQueryChanged(twitterCredentials.domain!!)

        testee.viewState.test {
            val awaitItem = awaitItem()
            assertNotNull(awaitItem.logins)
            assertEquals(1, awaitItem.logins?.size)
            assertEquals(twitterCredentials, awaitItem.logins?.get(0))
        }
    }

    @Test
    fun whenCredentialSelectedThenUpdateLastUsedTimestamp() = runTest {
        val credentials = twitterCredentials
        testee.onCredentialSelected(credentials)
        verify(autofillStore).updateCredentials(any(), refreshLastUpdatedTimestamp = eq(false))
    }

    private class FakeDomainListFilter : CredentialListFilter {
        override suspend fun filter(
            originalList: List<LoginCredentials>,
            query: String,
        ): List<LoginCredentials> {
            if (query.isBlank()) return originalList
            return originalList.filter { it.domain == query }
        }
    }
}
