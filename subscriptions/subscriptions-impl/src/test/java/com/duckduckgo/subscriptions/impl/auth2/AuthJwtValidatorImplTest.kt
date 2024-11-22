/*
 * Copyright (c) 2024 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.subscriptions.impl.auth2

import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test(expected = IllegalArgumentException::class)
    fun `when access token has incorrect issuer then validating throws IllegalArgumentException`() {
        authJwtValidator.validateAccessToken(
            jwt = FakeCredentialsWithIncorrectIssuer.ACCESS_TOKEN,
            jwkSet = FakeCredentialsWithIncorrectIssuer.JWK_SET,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when refresh token has incorrect issuer then validating throws IllegalArgumentException`() {
        authJwtValidator.validateRefreshToken(
            jwt = FakeCredentialsWithIncorrectIssuer.REFRESH_TOKEN,
            jwkSet = FakeCredentialsWithIncorrectIssuer.JWK_SET,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when access token has incorrect audience then validating throws IllegalArgumentException`() {
        authJwtValidator.validateAccessToken(
            jwt = FakeCredentialsWithIncorrectAudience.ACCESS_TOKEN,
            jwkSet = FakeCredentialsWithIncorrectAudience.JWK_SET,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when refresh token has incorrect audience then validating throws IllegalArgumentException`() {
        authJwtValidator.validateRefreshToken(
            jwt = FakeCredentialsWithIncorrectAudience.REFRESH_TOKEN,
            jwkSet = FakeCredentialsWithIncorrectAudience.JWK_SET,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when access token has incorrect scope then validating throws IllegalArgumentException`() {
        authJwtValidator.validateAccessToken(
            jwt = FakeCredentialsWithIncorrectScope.ACCESS_TOKEN,
            jwkSet = FakeCredentialsWithIncorrectScope.JWK_SET,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when refresh token has incorrect scope then validating throws IllegalArgumentException`() {
        authJwtValidator.validateRefreshToken(
            jwt = FakeCredentialsWithIncorrectScope.REFRESH_TOKEN,
            jwkSet = FakeCredentialsWithIncorrectScope.JWK_SET,
        )
    }

    private class FakeTimeProvider : CurrentTimeProvider {
        var currentTime: Instant = Instant.parse("2024-10-16T00:00:00.00Z")

        override fun currentTimeMillis(): Long = currentTime.toEpochMilli()
        override fun elapsedRealtime(): Long = throw UnsupportedOperationException()
        override fun localDateTimeNow(): LocalDateTime = throw UnsupportedOperationException()
    }

    companion object {
        const val ACCESS_TOKEN = "eyJhbGciOiJFUzI1NiIsImtpZCI6IjhmNzI2MzZkLTc4OGMtNGU5Ny1hYTZiLTEzZGU0YzNmYmU4MyIsInR5cCI6IkpXVCJ9.eyJhcGkiOiJ2MiIs" +
            "Imp0aSI6IlI0RjhHN3R4Tk1rNVNHUmx2Q2t6cmtFOHpna29iaHJ4TmpNcldtcElhWmRxa05EeUw0N01ET1FKSDVoV0hBSjR3NFUwREpxWHl1RHhleDA1cnI3cXVVOmMzYTVjMT" +
            "EzLTg3N2YtNDZkNC1hYzYzLWYyN2RiZWVjY2RhNCIsInNjb3BlIjoicHJpdmFjeXBybyIsImF1ZCI6IlByaXZhY3lQcm8iLCJpYXQiOjE3MzAzOTEwMzIsImlzcyI6Imh0dHBz" +
            "Oi8vcXVhY2suZHVja2R1Y2tnby5jb20iLCJzdWIiOiJmZjMwMGEyNS02OTNhLTQ3ZjUtODM5OC0wNjRmYzAwY2UzZGUiLCJlbWFpbCI6bnVsbCwiZXhwIjoxNzMwNDA1NDMyLC" +
            "JlbnRpdGxlbWVudHMiOlt7Im5hbWUiOiJzdWJzY3JpYmVyIiwiaWQiOjQsInByb2R1Y3QiOiJOZXR3b3JrIFByb3RlY3Rpb24ifSx7InByb2R1Y3QiOiJEYXRhIEJyb2tlciBQ" +
            "cm90ZWN0aW9uIiwibmFtZSI6InN1YnNjcmliZXIiLCJpZCI6NX0seyJwcm9kdWN0IjoiSWRlbnRpdHkgVGhlZnQgUmVzdG9yYXRpb24iLCJuYW1lIjoic3Vic2NyaWJlciIsIm" +
            "lkIjo2fV19.e7SRQ_IUnYSXFjd7npap80b2NttnHo-AIPTA42PuuEpWII4PLRmGQK-pGgxLta5gdQ7OU0z5Eikz6c1dUTAYjQ"

        const val REFRESH_TOKEN = "eyJhbGciOiJFUzI1NiIsImtpZCI6IjhmNzI2MzZkLTc4OGMtNGU5Ny1hYTZiLTEzZGU0YzNmYmU4MyIsInR5cCI6IkpXVCJ9.eyJzY29wZSI6InJ" +
            "lZnJlc2giLCJpYXQiOjE3MzAzOTEwMzIsImF1ZCI6IkF1dGgiLCJqdGkiOiJSNEY4Rzd0eE5NazVTR1JsdkNrenJrRTh6Z2tvYmhyeE5qTXJXbXBJYVpkcWtORHlMNDdNRE9RS" +
            "kg1aFdIQUo0dzRVMERKcVh5dUR4ZXgwNXJyN3F1VTo4MzIzZGE3Mi1jZmNlLTQyOGUtOWQwNS1hZjE0ODc1MzZjODEiLCJhcGkiOiJ2MiIsInN1YiI6ImZmMzAwYTI1LTY5M2E" +
            "tNDdmNS04Mzk4LTA2NGZjMDBjZTNkZSIsImlzcyI6Imh0dHBzOi8vcXVhY2suZHVja2R1Y2tnby5jb20iLCJleHAiOjE3MzI5ODMwMzJ9.J_ZbkwvK7ekBoBCDGTCbblekCPzC" +
            "nLWOmY0W1YzF0r31Ukl_xlBpADQVLzGbWpWV6IZHyxq1ufOIV-CLsYBMyw"

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

    private object FakeCredentialsWithIncorrectIssuer {
        const val ACCESS_TOKEN = "eyJraWQiOiJkMzI3ODU3NC02NDkyLTQ2ZmYtOGQ2NS1kYTk1YmIyOTFjYzQiLCJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJlbnRpdGxlbWVu" +
            "dHMiOlt7Im5hbWUiOiJzdWJzY3JpYmVyIiwiaWQiOjQsInByb2R1Y3QiOiJOZXR3b3JrIFByb3RlY3Rpb24ifSx7InByb2R1Y3QiOiJEYXRhIEJyb2tlciBQcm90ZWN0aW9uIi" +
            "wibmFtZSI6InN1YnNjcmliZXIiLCJpZCI6NX0seyJwcm9kdWN0IjoiSWRlbnRpdHkgVGhlZnQgUmVzdG9yYXRpb24iLCJuYW1lIjoic3Vic2NyaWJlciIsImlkIjo2fV0sImF1" +
            "ZCI6IlByaXZhY3lQcm8iLCJzdWIiOiJmZjMwMGEyNS02OTNhLTQ3ZjUtODM5OC0wNjRmYzAwY2UzZGUiLCJzY29wZSI6InByaXZhY3lwcm8iLCJpc3MiOiJodHRwczovL3F1YW" +
            "NrLmR1Y2tkdWNrZ28uY29tL3Rlc3QiLCJhcGkiOiJ2MiIsImV4cCI6MTczMDQyNzI1MiwiaWF0IjoxNzMwNDEyODUyLCJqdGkiOiJSNEY4Rzd0eE5NazVTR1JsdkNrenJrRTh6" +
            "Z2tvYmhyeE5qTXJXbXBJYVpkcWtORHlMNDdNRE9RSkg1aFdIQUo0dzRVMERKcVh5dUR4ZXgwNXJyN3F1VTpjM2E1YzExMy04NzdmLTQ2ZDQtYWM2My1mMjdkYmVlY2NkYTQiLC" +
            "JlbWFpbCI6bnVsbH0.q4-VSoafMcZMo4oKQ7nn2iSge3UO4Z5ALQi6E4qcJa8EAiM0P06CLAytL8Ev_TYfAtys_G9LwU1FYDObwCw8hA"

        const val REFRESH_TOKEN = "eyJraWQiOiJkMzI3ODU3NC02NDkyLTQ2ZmYtOGQ2NS1kYTk1YmIyOTFjYzQiLCJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJhdWQiOiJBdXR" +
            "oIiwic3ViIjoiZmYzMDBhMjUtNjkzYS00N2Y1LTgzOTgtMDY0ZmMwMGNlM2RlIiwic2NvcGUiOiJyZWZyZXNoIiwiaXNzIjoiaHR0cHM6Ly9xdWFjay5kdWNrZHVja2dvLmNvb" +
            "S90ZXN0IiwiYXBpIjoidjIiLCJleHAiOjE3MzI5ODMwMzIsImlhdCI6MTczMDM5MTAzMiwianRpIjoiUjRGOEc3dHhOTWs1U0dSbHZDa3pya0U4emdrb2JocnhOak1yV21wSWF" +
            "aZHFrTkR5TDQ3TURPUUpINWhXSEFKNHc0VTBESnFYeXVEeGV4MDVycjdxdVU6ODMyM2RhNzItY2ZjZS00MjhlLTlkMDUtYWYxNDg3NTM2YzgxIn0.3rA2bAC-Rh-vwe9bSQTEj" +
            "-jgHM1Dtx9PE7Cv8GellHw1wqIrLpRifRDJVYiDZ1XuHzdPfxpedCh9AOAN-mcL3w"

        const val JWK_SET = """{keys=[{kty=EC, crv=P-256, kid=d3278574-6492-46ff-8d65-da95bb291cc4, x=AM2J0W8piRoFBj17tvHcVtusFs6QQze9N_hGzBnpWZiv, y=
            |AJTXf8ndxPVg7vocTIPI3i23LeRCpvr5UhYOD86FOD86}]}"""
    }

    private object FakeCredentialsWithIncorrectAudience {
        const val ACCESS_TOKEN = "eyJraWQiOiIzNDJhMWI5OS1iNTUxLTRlYzAtOTc1Yy05ODRjNDBhZDMwNzMiLCJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJlbnRpdGxlbWVu" +
            "dHMiOlt7Im5hbWUiOiJzdWJzY3JpYmVyIiwiaWQiOjQsInByb2R1Y3QiOiJOZXR3b3JrIFByb3RlY3Rpb24ifSx7InByb2R1Y3QiOiJEYXRhIEJyb2tlciBQcm90ZWN0aW9uIi" +
            "wibmFtZSI6InN1YnNjcmliZXIiLCJpZCI6NX0seyJwcm9kdWN0IjoiSWRlbnRpdHkgVGhlZnQgUmVzdG9yYXRpb24iLCJuYW1lIjoic3Vic2NyaWJlciIsImlkIjo2fV0sImF1" +
            "ZCI6IlByaXZhY3lQcm9zIiwic3ViIjoiZmYzMDBhMjUtNjkzYS00N2Y1LTgzOTgtMDY0ZmMwMGNlM2RlIiwic2NvcGUiOiJwcml2YWN5cHJvIiwiaXNzIjoiaHR0cHM6Ly9xdW" +
            "Fjay5kdWNrZHVja2dvLmNvbSIsImFwaSI6InYyIiwiZXhwIjoxNzMwNDI3OTU1LCJpYXQiOjE3MzA0MTM1NTUsImp0aSI6IlI0RjhHN3R4Tk1rNVNHUmx2Q2t6cmtFOHpna29i" +
            "aHJ4TmpNcldtcElhWmRxa05EeUw0N01ET1FKSDVoV0hBSjR3NFUwREpxWHl1RHhleDA1cnI3cXVVOmMzYTVjMTEzLTg3N2YtNDZkNC1hYzYzLWYyN2RiZWVjY2RhNCIsImVtYW" +
            "lsIjpudWxsfQ.JqaJdZf9N_mhlqR04C0RO6R5O5yDLkjNAPHIcmn6KUuVm2j1izjCNwv4GX8D6WrFU9uXQywARaNC2o1wJQaBnw"

        const val REFRESH_TOKEN = "eyJraWQiOiIzNDJhMWI5OS1iNTUxLTRlYzAtOTc1Yy05ODRjNDBhZDMwNzMiLCJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJhdWQiOiJBdXR" +
            "oZW50aWNhdGlvbiIsInN1YiI6ImZmMzAwYTI1LTY5M2EtNDdmNS04Mzk4LTA2NGZjMDBjZTNkZSIsInNjb3BlIjoicmVmcmVzaCIsImlzcyI6Imh0dHBzOi8vcXVhY2suZHVja" +
            "2R1Y2tnby5jb20iLCJhcGkiOiJ2MiIsImV4cCI6MTczMzAwNTU1NSwiaWF0IjoxNzMwMzkxMDMyLCJqdGkiOiJSNEY4Rzd0eE5NazVTR1JsdkNrenJrRTh6Z2tvYmhyeE5qTXJ" +
            "XbXBJYVpkcWtORHlMNDdNRE9RSkg1aFdIQUo0dzRVMERKcVh5dUR4ZXgwNXJyN3F1VTo4MzIzZGE3Mi1jZmNlLTQyOGUtOWQwNS1hZjE0ODc1MzZjODEifQ.A1EP6hwV8Xusdf" +
            "d8hWKTADcurX3Y5SON5J98t4x9FyGDzu_fawoZLq9jmqXy0EDDP27iXtLt919QpPzFwCRo0w"

        const val JWK_SET = """{keys=[{kty=EC, crv=P-256, kid=342a1b99-b551-4ec0-975c-984c40ad3073, x=a0csfZFP45WHyCRW8IhNkmbtvgT4I5afXFuuAtnV75M, y=A
            |OMBpLXT0WIPmq1zME1NFONefBtwtXNJzM4mQaZDNXrO}]}"""
    }

    private object FakeCredentialsWithIncorrectScope {
        const val ACCESS_TOKEN = "eyJraWQiOiIzNzE2NzI5Yy0zMTM1LTRjOTItODhjNC1mYjEzYjlhMDkyOWMiLCJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJlbnRpdGxlbWVu" +
            "dHMiOlt7Im5hbWUiOiJzdWJzY3JpYmVyIiwiaWQiOjQsInByb2R1Y3QiOiJOZXR3b3JrIFByb3RlY3Rpb24ifSx7InByb2R1Y3QiOiJEYXRhIEJyb2tlciBQcm90ZWN0aW9uIi" +
            "wibmFtZSI6InN1YnNjcmliZXIiLCJpZCI6NX0seyJwcm9kdWN0IjoiSWRlbnRpdHkgVGhlZnQgUmVzdG9yYXRpb24iLCJuYW1lIjoic3Vic2NyaWJlciIsImlkIjo2fV0sImF1" +
            "ZCI6IlByaXZhY3lQcm8iLCJzdWIiOiJmZjMwMGEyNS02OTNhLTQ3ZjUtODM5OC0wNjRmYzAwY2UzZGUiLCJzY29wZSI6InBwcm8iLCJpc3MiOiJodHRwczovL3F1YWNrLmR1Y2" +
            "tkdWNrZ28uY29tIiwiYXBpIjoidjIiLCJleHAiOjE3MzA0Mjc3NTEsImlhdCI6MTczMDQxMzM1MSwianRpIjoiUjRGOEc3dHhOTWs1U0dSbHZDa3pya0U4emdrb2JocnhOak1y" +
            "V21wSWFaZHFrTkR5TDQ3TURPUUpINWhXSEFKNHc0VTBESnFYeXVEeGV4MDVycjdxdVU6YzNhNWMxMTMtODc3Zi00NmQ0LWFjNjMtZjI3ZGJlZWNjZGE0IiwiZW1haWwiOm51bG" +
            "x9.xJ1ZefISvIRW1ef7_dymx1eXwjML6ZW9BeieWEkAvRn6vQjgsu1glTaxrb0QHDsgHIZNoNW89Mk8QvHiyeHRuw"

        const val REFRESH_TOKEN = "eyJraWQiOiIzNzE2NzI5Yy0zMTM1LTRjOTItODhjNC1mYjEzYjlhMDkyOWMiLCJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJhdWQiOiJBdXR" +
            "oIiwic3ViIjoiZmYzMDBhMjUtNjkzYS00N2Y1LTgzOTgtMDY0ZmMwMGNlM2RlIiwic2NvcGUiOiJyZWZyZXNoaW5nIiwiaXNzIjoiaHR0cHM6Ly9xdWFjay5kdWNrZHVja2dvL" +
            "mNvbSIsImFwaSI6InYyIiwiZXhwIjoxNzMzMDA1MzUxLCJpYXQiOjE3MzAzOTEwMzIsImp0aSI6IlI0RjhHN3R4Tk1rNVNHUmx2Q2t6cmtFOHpna29iaHJ4TmpNcldtcElhWmR" +
            "xa05EeUw0N01ET1FKSDVoV0hBSjR3NFUwREpxWHl1RHhleDA1cnI3cXVVOjgzMjNkYTcyLWNmY2UtNDI4ZS05ZDA1LWFmMTQ4NzUzNmM4MSJ9.OEKrHqpgc6PVujwIHoj9OoDF" +
            "H-kRT79e0NE5eTmDwvnoHglweJigsKuWFxN6FoTfAKvC7tMPjy3EM3peYIRHXg"

        const val JWK_SET = """{keys=[{kty=EC, crv=P-256, kid=3716729c-3135-4c92-88c4-fb13b9a0929c, x=HFzQVBbfBdeOYsMdRSx_3MxbZowmDG1OwyiFBYRubgE, y=e
            |LlCqUJEVGgc0K7mjUo3Wk52C9mgRllnOfG_S6KSEck}]}"""
    }
}
