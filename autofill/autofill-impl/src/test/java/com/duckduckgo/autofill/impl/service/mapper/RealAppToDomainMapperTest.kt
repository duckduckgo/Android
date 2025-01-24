package com.duckduckgo.autofill.impl.service.mapper

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.store.targets.DomainTargetAppDao
import com.duckduckgo.autofill.store.targets.DomainTargetAppEntity
import com.duckduckgo.autofill.store.targets.TargetApp
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealAppToDomainMapperTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var dao: DomainTargetAppDao

    @Mock
    private lateinit var fingerprintProvider: AppFingerprintProvider

    @Mock
    private lateinit var currentTimeProvider: CurrentTimeProvider

    @Mock
    private lateinit var assetLinksLoader: AssetLinksLoader

    private lateinit var toTest: RealAppToDomainMapper

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        toTest = RealAppToDomainMapper(
            dao,
            fingerprintProvider,
            assetLinksLoader,
            coroutineTestRule.testDispatcherProvider,
            currentTimeProvider,
        )

        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1737464996667)
    }

    @Test
    fun whenAppWithValidCredentialsExistInDatasetThenReturnAllMatchingDomains() = runTest {
        whenever(fingerprintProvider.getSHA256HexadecimalFingerprint("com.real.app")).thenReturn(listOf("realfingerprint"))
        whenever(dao.getDomainsForApp("com.real.app", listOf("realfingerprint"))).thenReturn(
            listOf(
                "dataset-domain-2.com",
                "dataset-domain.com",
            ),
        )

        val result = toTest.getAssociatedDomains("com.real.app")

        assertTrue(result.isNotEmpty())
        assertEquals(2, result.size)
        assertTrue(result.contains("dataset-domain-2.com"))
        assertTrue(result.contains("dataset-domain.com"))
    }

    @Test
    fun whenMaliciousAppWithInvalidFingerprintThenReturnNoDomain() = runTest {
        whenever(fingerprintProvider.getSHA256HexadecimalFingerprint("com.potentially.malicious.app")).thenReturn(listOf("fakefingerprint"))
        whenever(dao.getDomainsForApp("com.potentially.malicious.app", listOf("realfingerprint"))).thenReturn(listOf("dataset-domain.com"))
        whenever(assetLinksLoader.getValidTargetApps("potentially.com")).thenReturn(
            mapOf("com.potentially.malicious.app" to listOf("realfingerprint")),
        )

        val result = toTest.getAssociatedDomains("com.potentially.malicious.app")

        // Doesn't match dataset and assetlinks, despite package being present in both due to invalid fingerprint
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenValidAppIsNotInDatasetButHasValidReversedPackageDomainThenReturnAllMatchingDomains() = runTest {
        whenever(fingerprintProvider.getSHA256HexadecimalFingerprint("com.website.app")).thenReturn(listOf("realfingerprint"))
        whenever(dao.getDomainsForApp("com.website.app", listOf("fakefingerprint"))).thenReturn(emptyList())
        whenever(assetLinksLoader.getValidTargetApps("website.com")).thenReturn(
            mapOf(
                "com.website.app" to listOf(
                    "realfingerprint",
                    "realfingerprint2",
                ),
            ),
        )

        val result = toTest.getAssociatedDomains("com.website.app")

        assertTrue(result.isNotEmpty())
        assertEquals(1, result.size)
        assertTrue(result.contains("website.com"))
        // Check data is persisted
        verify(dao).insertAllMapping(
            listOf(
                DomainTargetAppEntity(
                    domain = "website.com",
                    targetApp = TargetApp(
                        packageName = "com.website.app",
                        sha256CertFingerprints = "realfingerprint",
                    ),
                    dataExpiryInMillis = 1737464996667 + TimeUnit.DAYS.toMillis(30),
                ),
                DomainTargetAppEntity(
                    domain = "website.com",
                    targetApp = TargetApp(
                        packageName = "com.website.app",
                        sha256CertFingerprints = "realfingerprint2",
                    ),
                    dataExpiryInMillis = 1737464996667 + TimeUnit.DAYS.toMillis(30),
                ),
            ),
        )
    }

    @Test
    fun whenAppIsNotInDatasetAndHasNoAssetlinksThenReturnNoDomain() = runTest {
        whenever(fingerprintProvider.getSHA256HexadecimalFingerprint("com.website-invalid.app")).thenReturn(listOf("realfingerprint"))
        whenever(dao.getDomainsForApp("com.website-invalid.app", listOf("fakefingerprint"))).thenReturn(emptyList())
        whenever(assetLinksLoader.getValidTargetApps("website-invalid.com")).thenReturn(emptyMap())

        val result = toTest.getAssociatedDomains("com.website-invalid.app")

        // Doesn't match dataset but matches assetlinks
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenUnableToGetFingerprintThenReturnEmpty() = runTest {
        whenever(fingerprintProvider.getSHA256HexadecimalFingerprint("com.no.app")).thenReturn(emptyList())

        val result = toTest.getAssociatedDomains("com.no.fingerprint")

        assertTrue(result.isEmpty())
        verifyNoInteractions(dao)
        verifyNoInteractions(assetLinksLoader)
    }

    @Test
    fun whenAppPackageYieldsNullDomainWhenReversedThenReturnEmpty() = runTest {
        whenever(fingerprintProvider.getSHA256HexadecimalFingerprint("com")).thenReturn(listOf("realfingerprint"))

        val result = toTest.getAssociatedDomains("com")

        assertTrue(result.isEmpty())
    }
}
