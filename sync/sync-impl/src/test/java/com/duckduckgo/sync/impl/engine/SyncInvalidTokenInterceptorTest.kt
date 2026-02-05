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

package com.duckduckgo.sync.impl.engine

import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.sync.impl.API_CODE.INVALID_LOGIN_CREDENTIALS
import com.duckduckgo.sync.impl.SyncService.Companion.SYNC_PROD_ENVIRONMENT_URL
import com.duckduckgo.sync.impl.engine.SyncInvalidTokenInterceptor.Companion.SYNC_USER_LOGGED_OUT_NOTIFICATION_ID
import okhttp3.Interceptor.Chain
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncInvalidTokenInterceptorTest {

    @JvmField @Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationManager = NotificationManagerCompat.from(context)
    private val syncNotificationBuilder = FakeNotificationBuilder()
    private val invalidTokenInterceptor = SyncInvalidTokenInterceptor(
        context,
        notificationManager,
        syncNotificationBuilder,
    )

    @Test
    fun whenInterceptingSyncResponseInvalidTokenWhenGETThenNotifyUser() {
        val chain = givenGetRequest(SYNC_PROD_ENVIRONMENT_URL, INVALID_LOGIN_CREDENTIALS.code)

        invalidTokenInterceptor.intercept(chain)

        notificationManager.activeNotifications
            .find { it.id == SYNC_USER_LOGGED_OUT_NOTIFICATION_ID } ?: fail("Notification not found")
    }

    @Test
    fun whenInterceptingSyncResponseInvalidTokenWhenPATCHThenNotifyUser() {
        val chain = givenPatchRequest(SYNC_PROD_ENVIRONMENT_URL, INVALID_LOGIN_CREDENTIALS.code)

        invalidTokenInterceptor.intercept(chain)

        notificationManager.activeNotifications
            .find { it.id == SYNC_USER_LOGGED_OUT_NOTIFICATION_ID } ?: fail("Notification not found")
    }

    @Test
    fun whenInterceptingOtherResponseCodeThenDoNotNotifyUser() {
        val chain = givenGetRequest(SYNC_PROD_ENVIRONMENT_URL, 400)

        invalidTokenInterceptor.intercept(chain)

        assertNull(notificationManager.activeNotifications.find { it.id == SYNC_USER_LOGGED_OUT_NOTIFICATION_ID })
    }

    @Test
    fun whenInterceptingNonSyncGetRequestThenDoNotNotifyUser() {
        val chain = givenGetRequest("https://www.example.com", INVALID_LOGIN_CREDENTIALS.code)

        invalidTokenInterceptor.intercept(chain)

        assertNull(notificationManager.activeNotifications.find { it.id == SYNC_USER_LOGGED_OUT_NOTIFICATION_ID })
    }

    @Test
    fun whenInterceptingNonSyncPatchRequestThenDoNotNotifyUser() {
        val chain = givenPatchRequest("https://www.example.com", INVALID_LOGIN_CREDENTIALS.code)

        invalidTokenInterceptor.intercept(chain)

        assertNull(notificationManager.activeNotifications.find { it.id == SYNC_USER_LOGGED_OUT_NOTIFICATION_ID })
    }

    @Test
    fun whenInterceptingSyncResponseInvalidTokenWhenDELETEThenNotifyUser() {
        val chain = givenDeleteRequest(SYNC_PROD_ENVIRONMENT_URL, INVALID_LOGIN_CREDENTIALS.code)

        invalidTokenInterceptor.intercept(chain)

        notificationManager.activeNotifications
            .find { it.id == SYNC_USER_LOGGED_OUT_NOTIFICATION_ID } ?: fail("Notification not found")
    }

    @Test
    fun whenInterceptingNonSyncDeleteRequestThenDoNotNotifyUser() {
        val chain = givenDeleteRequest("https://www.example.com", INVALID_LOGIN_CREDENTIALS.code)

        invalidTokenInterceptor.intercept(chain)

        assertNull(notificationManager.activeNotifications.find { it.id == SYNC_USER_LOGGED_OUT_NOTIFICATION_ID })
    }

    @Test
    fun whenInterceptingSyncResponseInvalidTokenWhenPOSTThenNotifyUser() {
        val chain = givenPostRequest(SYNC_PROD_ENVIRONMENT_URL, INVALID_LOGIN_CREDENTIALS.code)

        invalidTokenInterceptor.intercept(chain)

        notificationManager.activeNotifications
            .find { it.id == SYNC_USER_LOGGED_OUT_NOTIFICATION_ID } ?: fail("Notification not found")
    }

    @Test
    fun whenInterceptingNonSyncPostRequestThenDoNotNotifyUser() {
        val chain = givenPostRequest("https://www.example.com", INVALID_LOGIN_CREDENTIALS.code)

        invalidTokenInterceptor.intercept(chain)

        assertNull(notificationManager.activeNotifications.find { it.id == SYNC_USER_LOGGED_OUT_NOTIFICATION_ID })
    }

    private fun givenGetRequest(
        url: String,
        expectedResponseCode: Int? = null,
    ): Chain {
        return object : FakeChain(url, expectedResponseCode) {
            override fun request() = Request.Builder().url(url).method("GET", null).build()
        }
    }

    private fun givenPatchRequest(
        url: String,
        expectedResponseCode: Int? = null,
    ): Chain {
        return object : FakeChain(url, expectedResponseCode) {
            override fun request() = Request.Builder().url(url).method("PATCH", "".toRequestBody()).build()
        }
    }

    private fun givenDeleteRequest(
        url: String,
        expectedResponseCode: Int? = null,
    ): Chain {
        return object : FakeChain(url, expectedResponseCode) {
            override fun request() = Request.Builder().url(url).method("DELETE", null).build()
        }
    }

    private fun givenPostRequest(
        url: String,
        expectedResponseCode: Int? = null,
    ): Chain {
        return object : FakeChain(url, expectedResponseCode) {
            override fun request() = Request.Builder().url(url).method("POST", "".toRequestBody()).build()
        }
    }
}
