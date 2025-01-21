package com.duckduckgo.autofill.impl.service.mapper

import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealAssetLinksLoaderTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var service: AssetLinksService
    private lateinit var toTest: RealAssetLinksLoader

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        toTest = RealAssetLinksLoader(service, coroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun whenServiceThrowsAnExceptionTheReturnEmptyMap() = runTest {
        whenever(service.getAssetLinks("not-supported.com")).thenThrow(RuntimeException())

        assertTrue(toTest.getValidTargetApps("not-supported.com").isEmpty())
    }

    @Test
    fun whenServiceReturnsAssetlinksTheReturnMapWithValidTargetsOnnly() = runTest {
        whenever(service.getAssetLinks("https://supported.com/.well-known/assetlinks.json")).thenReturn(
            listOf(
                AssetLink(
                    relation = listOf("delegate_permission/common.handle_all_urls"),
                    target = AssetLinkTarget(
                        namespace = "android_app",
                        package_name = "app.no.valid.relation",
                        sha256_cert_fingerprints = listOf("fingerprint"),
                    ),
                ),
                AssetLink(
                    relation = listOf("delegate_permission/common.get_login_creds"),
                    target = AssetLinkTarget(
                        namespace = "web",
                        package_name = null,
                        sha256_cert_fingerprints = null,
                    ),
                ),
                AssetLink(
                    relation = listOf("delegate_permission/common.get_login_creds"),
                    target = AssetLinkTarget(
                        namespace = "android_app",
                        package_name = "app.valid",
                        sha256_cert_fingerprints = listOf("fingerprint", "fingerprint2"),
                    ),
                ),
                AssetLink(
                    relation = listOf("delegate_permission/common.handle_all_urls", "delegate_permission/common.get_login_creds"),
                    target = AssetLinkTarget(
                        namespace = "android_app",
                        package_name = "app.valid.2",
                        sha256_cert_fingerprints = listOf("fingerprint"),
                    ),
                ),
                AssetLink(
                    relation = listOf("delegate_permission/common.handle_all_urls", "delegate_permission/common.get_login_creds"),
                    target = AssetLinkTarget(
                        namespace = "android_app",
                        package_name = "app.no.fingerprint",
                        sha256_cert_fingerprints = null,
                    ),
                ),
                AssetLink(
                    relation = listOf("delegate_permission/common.handle_all_urls", "delegate_permission/common.get_login_creds"),
                    target = AssetLinkTarget(
                        namespace = "android_app",
                        package_name = "app.empty.fingerprint",
                        sha256_cert_fingerprints = emptyList(),
                    ),
                ),
                AssetLink(
                    relation = listOf("delegate_permission/common.handle_all_urls", "delegate_permission/common.get_login_creds"),
                    target = AssetLinkTarget(
                        namespace = "android_app",
                        package_name = null,
                        sha256_cert_fingerprints = listOf("fingerprint"),
                    ),
                ),
            ),
        )

        toTest.getValidTargetApps("supported.com").let {
            assertTrue(it.isNotEmpty())
            assertEquals(2, it.size)
            assertEquals(listOf("fingerprint", "fingerprint2"), it["app.valid"])
            assertEquals(listOf("fingerprint"), it["app.valid.2"])
        }
    }
}
