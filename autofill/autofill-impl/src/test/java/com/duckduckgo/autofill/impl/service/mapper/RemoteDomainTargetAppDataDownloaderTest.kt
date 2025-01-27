package com.duckduckgo.autofill.impl.service.mapper

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.autofill.store.targets.DomainTargetAppDao
import com.duckduckgo.autofill.store.targets.DomainTargetAppEntity
import com.duckduckgo.autofill.store.targets.TargetApp
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class RemoteDomainTargetAppDataDownloaderTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var remoteDomainTargetAppService: RemoteDomainTargetAppService

    @Mock
    private lateinit var autofillPrefsStore: AutofillPrefsStore

    @Mock
    private lateinit var domainTargetAppDao: DomainTargetAppDao

    @Mock
    private lateinit var currentTimeProvider: CurrentTimeProvider

    @Mock
    private lateinit var mockOwner: LifecycleOwner
    private lateinit var toTest: RemoteDomainTargetAppDataDownloader
    private val dataset = RemoteDomainTargetDataSet(
        version = 1,
        targets = listOf(
            RemoteDomainTarget(
                url = "hello.com",
                apps = listOf(
                    RemoteTargetApp(
                        package_name = "app.valid",
                        sha256_cert_fingerprints = listOf("fingerprint"),
                    ),
                ),
            ),

            RemoteDomainTarget(
                url = "shared.com",
                apps = listOf(
                    RemoteTargetApp(
                        package_name = "app.valid",
                        sha256_cert_fingerprints = listOf("fingerprint"),
                    ),
                ),
            ),
            RemoteDomainTarget(
                url = "hello2.com",
                apps = listOf(
                    RemoteTargetApp(
                        package_name = "app.valid2",
                        sha256_cert_fingerprints = listOf("fingerprint", "fingerprint2"),
                    ),
                    RemoteTargetApp(
                        package_name = "app.valid3",
                        sha256_cert_fingerprints = listOf("fingerprint3"),
                    ),
                ),
            ),
        ),
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        toTest = RemoteDomainTargetAppDataDownloader(
            coroutineTestRule.testScope,
            coroutineTestRule.testDispatcherProvider,
            remoteDomainTargetAppService,
            autofillPrefsStore,
            domainTargetAppDao,
            currentTimeProvider,
        )

        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1737548373455)
    }

    @Test
    fun whenOnMainLifecycleCreateThenDownloadAndPersistData() = runTest {
        whenever(autofillPrefsStore.domainTargetDatasetVersion).thenReturn(0)
        whenever(remoteDomainTargetAppService.fetchDataset()).thenReturn(dataset)

        toTest.onCreate(mockOwner)

        verify(remoteDomainTargetAppService).fetchDataset()
        verify(autofillPrefsStore).domainTargetDatasetVersion = 1
        verify(domainTargetAppDao).deleteAllExpired(1737548373455)
        verify(domainTargetAppDao).updateRemote(
            listOf(
                DomainTargetAppEntity(
                    domain = "hello.com",
                    targetApp = TargetApp(
                        packageName = "app.valid",
                        sha256CertFingerprints = "fingerprint",
                    ),
                    dataExpiryInMillis = 0L,
                ),
                DomainTargetAppEntity(
                    domain = "shared.com",
                    targetApp = TargetApp(
                        packageName = "app.valid",
                        sha256CertFingerprints = "fingerprint",
                    ),
                    dataExpiryInMillis = 0L,
                ),
                DomainTargetAppEntity(
                    domain = "hello2.com",
                    targetApp = TargetApp(
                        packageName = "app.valid2",
                        sha256CertFingerprints = "fingerprint",
                    ),
                    dataExpiryInMillis = 0L,
                ),
                DomainTargetAppEntity(
                    domain = "hello2.com",
                    targetApp = TargetApp(
                        packageName = "app.valid2",
                        sha256CertFingerprints = "fingerprint2",
                    ),
                    dataExpiryInMillis = 0L,
                ),
                DomainTargetAppEntity(
                    domain = "hello2.com",
                    targetApp = TargetApp(
                        packageName = "app.valid3",
                        sha256CertFingerprints = "fingerprint3",
                    ),
                    dataExpiryInMillis = 0L,
                ),
            ),
        )
    }

    @Test
    fun whenVersionIsTheSameThenDontPersistData() = runTest {
        whenever(autofillPrefsStore.domainTargetDatasetVersion).thenReturn(1)
        whenever(remoteDomainTargetAppService.fetchDataset()).thenReturn(dataset)

        toTest.onCreate(mockOwner)

        verify(remoteDomainTargetAppService).fetchDataset()
        verify(domainTargetAppDao).deleteAllExpired(1737548373455)
        verifyNoMoreInteractions(domainTargetAppDao)
    }
}
