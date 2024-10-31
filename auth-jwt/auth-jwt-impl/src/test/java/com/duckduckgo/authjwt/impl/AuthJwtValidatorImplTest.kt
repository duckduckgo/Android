package com.duckduckgo.authjwt.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.authjwt.api.Entitlement
import com.duckduckgo.common.utils.CurrentTimeProvider
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthJwtValidatorImplTest {

    private val timeProvider = FakeTimeProvider()
    private val authJwtValidator = AuthJwtValidatorImpl(timeProvider)

    @Test
    fun `when valid access token then returns claims`() {
        val claims = authJwtValidator.validateAccessToken(ACCESS_TOKEN, JWK_SET)

        val expectedEntitlements = listOf(
            Entitlement(name = "subscriber", product = "Network Protection"),
            Entitlement(name = "subscriber", product = "Data Broker Protection"),
            Entitlement(name = "subscriber", product = "Identity Theft Restoration"),
        )

        assertEquals(Instant.parse("2024-10-31T20:10:32Z"), claims.expiresAt)
        assertEquals("ff300a25-693a-47f5-8398-064fc00ce3de", claims.accountExternalId)
        assertEquals(expectedEntitlements, claims.entitlements)
        assertNull(claims.email)
    }

    @Test
    fun `when valid refresh token then returns claims`() {
        val claims = authJwtValidator.validateRefreshToken(REFRESH_TOKEN, JWK_SET)

        assertEquals(Instant.parse("2024-11-30T16:10:32Z"), claims.expiresAt)
        assertEquals("ff300a25-693a-47f5-8398-064fc00ce3de", claims.accountExternalId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when access token is expired then throws IllegalArgumentException`() {
        timeProvider.currentTime = Instant.parse("2024-11-16T00:00:00.00Z")
        authJwtValidator.validateAccessToken(ACCESS_TOKEN, JWK_SET)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when refresh token is expired then throws IllegalArgumentException`() {
        timeProvider.currentTime = Instant.parse("2024-12-01T00:00:00.00Z")
        authJwtValidator.validateRefreshToken(REFRESH_TOKEN, JWK_SET)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when JWK set does not contain key with matching id then validating access token throws IllegalArgumentException`() {
        // kid of the single key does not match kid in token header
        val jwkSet = """
            {
                "keys": [
                    {
                        "crv": "P-256",
                        "kid": "aa4c0019-9da9-4143-9866-3f7b54224a46",
                        "kty": "EC",
                        "ts": 1722282670,
                        "x": "kN2BXRyRbylNSaw3CrZKiKdATXjF1RIp2FpOxYMeuWg",
                        "y": "wovX-ifQuoKKAi-ZPYFcZ9YBhCxN_Fng3qKSW2wKpdg"
                    }
                ]
            }
            """

        authJwtValidator.validateAccessToken(ACCESS_TOKEN, jwkSet)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when JWK set does not contain key with matching id then validating refresh token throws IllegalArgumentException`() {
        // kid of the single key does not match kid in token header
        val jwkSet = """
            {
                "keys": [
                    {
                        "crv": "P-256",
                        "kid": "aa4c0019-9da9-4143-9866-3f7b54224a46",
                        "kty": "EC",
                        "ts": 1722282670,
                        "x": "kN2BXRyRbylNSaw3CrZKiKdATXjF1RIp2FpOxYMeuWg",
                        "y": "wovX-ifQuoKKAi-ZPYFcZ9YBhCxN_Fng3qKSW2wKpdg"
                    }
                ]
            }
            """

        authJwtValidator.validateRefreshToken(REFRESH_TOKEN, jwkSet)
    }

    private class FakeTimeProvider : CurrentTimeProvider {
        var currentTime: Instant = Instant.parse("2024-10-16T00:00:00.00Z")

        override fun currentTimeMillis(): Long = currentTime.toEpochMilli()
        override fun elapsedRealtime(): Long = throw UnsupportedOperationException()
        override fun localDateTimeNow(): LocalDateTime = throw UnsupportedOperationException()
    }

    companion object {
        const val ACCESS_TOKEN = "eyJhbGciOiJFUzI1NiIsImtpZCI6IjhmNzI2MzZkLTc4OGMtNGU5Ny1hYTZiLTEzZGU0YzNmYmU4MyIsInR5cCI6IkpXVCJ9.eyJhcGkiOiJ2MiIsImp0aSI6IlI0RjhHN3R4Tk1rNVNHUmx2Q2t6cmtFOHpna29iaHJ4TmpNcldtcElhWmRxa05EeUw0N01ET1FKSDVoV0hBSjR3NFUwREpxWHl1RHhleDA1cnI3cXVVOmMzYTVjMTEzLTg3N2YtNDZkNC1hYzYzLWYyN2RiZWVjY2RhNCIsInNjb3BlIjoicHJpdmFjeXBybyIsImF1ZCI6IlByaXZhY3lQcm8iLCJpYXQiOjE3MzAzOTEwMzIsImlzcyI6Imh0dHBzOi8vcXVhY2suZHVja2R1Y2tnby5jb20iLCJzdWIiOiJmZjMwMGEyNS02OTNhLTQ3ZjUtODM5OC0wNjRmYzAwY2UzZGUiLCJlbWFpbCI6bnVsbCwiZXhwIjoxNzMwNDA1NDMyLCJlbnRpdGxlbWVudHMiOlt7Im5hbWUiOiJzdWJzY3JpYmVyIiwiaWQiOjQsInByb2R1Y3QiOiJOZXR3b3JrIFByb3RlY3Rpb24ifSx7InByb2R1Y3QiOiJEYXRhIEJyb2tlciBQcm90ZWN0aW9uIiwibmFtZSI6InN1YnNjcmliZXIiLCJpZCI6NX0seyJwcm9kdWN0IjoiSWRlbnRpdHkgVGhlZnQgUmVzdG9yYXRpb24iLCJuYW1lIjoic3Vic2NyaWJlciIsImlkIjo2fV19.e7SRQ_IUnYSXFjd7npap80b2NttnHo-AIPTA42PuuEpWII4PLRmGQK-pGgxLta5gdQ7OU0z5Eikz6c1dUTAYjQ"
        const val REFRESH_TOKEN = "eyJhbGciOiJFUzI1NiIsImtpZCI6IjhmNzI2MzZkLTc4OGMtNGU5Ny1hYTZiLTEzZGU0YzNmYmU4MyIsInR5cCI6IkpXVCJ9.eyJzY29wZSI6InJlZnJlc2giLCJpYXQiOjE3MzAzOTEwMzIsImF1ZCI6IkF1dGgiLCJqdGkiOiJSNEY4Rzd0eE5NazVTR1JsdkNrenJrRTh6Z2tvYmhyeE5qTXJXbXBJYVpkcWtORHlMNDdNRE9RSkg1aFdIQUo0dzRVMERKcVh5dUR4ZXgwNXJyN3F1VTo4MzIzZGE3Mi1jZmNlLTQyOGUtOWQwNS1hZjE0ODc1MzZjODEiLCJhcGkiOiJ2MiIsInN1YiI6ImZmMzAwYTI1LTY5M2EtNDdmNS04Mzk4LTA2NGZjMDBjZTNkZSIsImlzcyI6Imh0dHBzOi8vcXVhY2suZHVja2R1Y2tnby5jb20iLCJleHAiOjE3MzI5ODMwMzJ9.J_ZbkwvK7ekBoBCDGTCbblekCPzCnLWOmY0W1YzF0r31Ukl_xlBpADQVLzGbWpWV6IZHyxq1ufOIV-CLsYBMyw"

        const val JWK_SET = """
            {
                "keys": [
                    {
                        "alg": "ES256",
                        "crv": "P-256",
                        "kid": "8f72636d-788c-4e97-aa6b-13de4c3fbe83",
                        "kty": "EC",
                        "ts": 1727109705,
                        "x": "Vxr40N9U31pEaw0mER3zzYgzxnDsPyuQ4jB7sHNP_Bo",
                        "y": "hdWZPAsbNp_r0l7PaBB8s5ndnttE0YOjSKRJ4y0exjQ"
                    },
                    {
                        "crv": "P-256",
                        "kid": "759581d8-c13d-48ce-b498-536980e84a4c",
                        "kty": "EC",
                        "ts": 1722282670,
                        "x": "ixebCE6b0IaJwe8zb4qNMzJ3dfwv2tsAo4G5C7wtf0s",
                        "y": "hGw00p-0nLhxUVlB2Ko2oOu0E6XE-cEmWLBGzP2Ck_4"
                    }
                ]
            }
        """
    }
}
