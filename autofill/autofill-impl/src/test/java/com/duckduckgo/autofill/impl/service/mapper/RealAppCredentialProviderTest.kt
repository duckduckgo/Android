package com.duckduckgo.autofill.impl.service.mapper

import android.annotation.SuppressLint
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.service.AutofillServiceFeature
import com.duckduckgo.autofill.impl.service.mapper.fakes.FakeAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RealAppCredentialProviderTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var mapper: AppToDomainMapper

    private val autofillServiceFeature = FakeFeatureToggleFactory.create(AutofillServiceFeature::class.java)

    private lateinit var toTest: RealAppCredentialProvider

    private val store = FakeAutofillStore(
        listOf(
            LoginCredentials(
                domain = "package1.com",
                username = "username1",
                password = "password1",
            ),
            LoginCredentials(
                domain = "package2.com",
                username = "username2",
                password = "password2",
            ),
        ),
    )

    @Before
    fun setUp() {
        autofillServiceFeature.self().setRawStoredState(State(enable = true))
        autofillServiceFeature.canMapAppToDomain().setRawStoredState(State(enable = true))
        MockitoAnnotations.openMocks(this)
        toTest = RealAppCredentialProvider(
            mapper,
            coroutineTestRule.testDispatcherProvider,
            store,
            autofillServiceFeature,
        )
    }

    @Test
    fun whenFeatureFlagDisabledThenReturnEmtpyList() = runTest {
        autofillServiceFeature.canMapAppToDomain().setRawStoredState(State(enable = false))
        whenever(mapper.getAssociatedDomains("com.duplicate.domains")).thenReturn(listOf("package1.com", "package2.com", "package2.com"))

        val result = toTest.getCredentials("com.duplicate.domains")

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenAppIsMappedToDuplicateDomainsReturnDistinctCredentials() = runTest {
        whenever(mapper.getAssociatedDomains("com.duplicate.domains")).thenReturn(listOf("package1.com", "package2.com", "package2.com"))

        val result = toTest.getCredentials("com.duplicate.domains")

        assertNotNull(result)
        assertEquals(2, result.count())
        assertNotNull(result.find { it.domain == "package1.com" })
        assertNotNull(result.find { it.domain == "package2.com" })
    }

    @Test
    fun whenAppHasUniqueAssociatedDomainAndCredentialReturnDistinctCredentials() = runTest {
        whenever(mapper.getAssociatedDomains("com.distinct.domain")).thenReturn(listOf("package2.com"))

        val result = toTest.getCredentials("com.distinct.domain")

        assertEquals(1, result.count())
        assertNotNull(result.find { it.domain == "package2.com" })
    }

    @Test
    fun whenAppHasNoAssociatedDomainsReturnEmptyList() = runTest {
        whenever(mapper.getAssociatedDomains("com.no.domain")).thenReturn(emptyList())

        val result = toTest.getCredentials("com.no.domain")

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenAssociatedDomainHasNoCredentialReturnEmptyList() = runTest {
        whenever(mapper.getAssociatedDomains("com.no.credential")).thenReturn(listOf("package4.com"))

        val result = toTest.getCredentials("com.no.credential")

        assertTrue(result.isEmpty())
    }
}
