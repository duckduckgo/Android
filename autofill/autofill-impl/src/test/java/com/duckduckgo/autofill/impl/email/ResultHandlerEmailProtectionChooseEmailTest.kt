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

package com.duckduckgo.autofill.impl.email

import android.os.Bundle
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.COHORT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.LAST_USED_DAY
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog.UseEmailResultType.DoNotUseEmailProtection
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog.UseEmailResultType.UsePersonalEmailAddress
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog.UseEmailResultType.UsePrivateAliasAddress
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.engagement.DataAutofilledListener
import com.duckduckgo.autofill.impl.partialsave.PartialCredentialSaveStore
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_TOOLTIP_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_USE_ADDRESS
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_USE_ALIAS
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class ResultHandlerEmailProtectionChooseEmailTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val callback: AutofillEventListener = mock()
    private val appBuildConfig: AppBuildConfig = mock()

    private val emailManager: EmailManager = mock()
    private val pixel: Pixel = mock()
    private val partialCredentialSaveStore: PartialCredentialSaveStore = mock()
    private val webView: WebView = mock()

    private val testee = ResultHandlerEmailProtectionChooseEmail(
        appBuildConfig = appBuildConfig,
        emailManager = emailManager,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appCoroutineScope = coroutineTestRule.testScope,
        pixel = pixel,
        autofilledListeners = FakePluginPoint(),
        partialCredentialSaveStore = partialCredentialSaveStore,
    )

    @Before
    fun before() {
        whenever(emailManager.getEmailAddress()).thenReturn("personal-example@duck.com")
        whenever(emailManager.getAlias()).thenReturn("private-example@duck.com")
        whenever(emailManager.getCohort()).thenReturn("cohort")
        whenever(emailManager.getLastUsedDate()).thenReturn("2021-01-01")
    }

    @Test
    fun whenUserSelectedToUsePersonalAddressThenCorrectCallbackInvoked() = runTest {
        val bundle = bundle(result = UsePersonalEmailAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(callback).onUseEmailProtectionPersonalAddress(any(), any())
    }

    @Test
    fun whenUserSelectedToUsePersonalAddressThenPartialUsernameSaveMade() = runTest {
        val bundle = bundle(result = UsePersonalEmailAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(partialCredentialSaveStore).saveUsername(url = any(), username = eq("personal-example@duck.com"))
    }

    @Test
    fun whenUserSelectedToUsePrivateAliasAddressThenCorrectCallbackInvoked() = runTest {
        val bundle = bundle(result = UsePrivateAliasAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(callback).onUseEmailProtectionPrivateAlias(any(), any())
    }

    @Test
    fun whenUserSelectedToUsePrivateAliasAddressThenPartialUsernameSaveMade() = runTest {
        val bundle = bundle(result = UsePrivateAliasAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(partialCredentialSaveStore).saveUsername(url = any(), username = eq("private-example@duck.com"))
    }

    @Test
    fun whenUrlMissingFromBundleThenExceptionThrown() = runTest {
        val bundle = bundle(url = null, result = UsePersonalEmailAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verifyNoInteractions(callback)
    }

    @Test
    fun whenResultTypeMissingFromBundleThenExceptionThrown() = runTest {
        val bundle = bundle(result = null)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verifyNoInteractions(callback)
    }

    @Test
    fun whenUserSelectedToUsePrivateAliasAddressThenSetNewLastUsedDateCalled() = runTest {
        val bundle = bundle(result = UsePrivateAliasAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(emailManager).setNewLastUsedDate()
    }

    @Test
    fun whenUserSelectedToUsePersonalDuckAddressThenSetNewLastUsedDateCalled() = runTest {
        val bundle = bundle(result = UsePersonalEmailAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(emailManager).setNewLastUsedDate()
    }

    @Test
    fun whenUserSelectedNotToUseEmailProtectionThenPixelSent() = runTest {
        val bundle = bundle(result = DoNotUseEmailProtection)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(pixel).enqueueFire(EMAIL_TOOLTIP_DISMISSED, mapOf(COHORT to "cohort"))
    }

    @Test
    fun whenUserSelectedToUsePersonalDuckAddressThenPixelSent() = runTest {
        val bundle = bundle(result = UsePersonalEmailAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(pixel).enqueueFire(
            EMAIL_USE_ADDRESS,
            mapOf(COHORT to "cohort", LAST_USED_DAY to "2021-01-01"),
        )
    }

    @Test
    fun whenUserSelectedToUsePrivateAliasThenPixelSent() = runTest {
        val bundle = bundle(result = UsePrivateAliasAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback, webView)
        verify(pixel).enqueueFire(
            EMAIL_USE_ALIAS,
            mapOf(COHORT to "cohort", LAST_USED_DAY to "2021-01-01"),
        )
    }

    private fun bundle(
        url: String? = "example.com",
        result: EmailProtectionChooseEmailDialog.UseEmailResultType?,
    ): Bundle {
        return Bundle().also {
            it.putString(EmailProtectionChooseEmailDialog.KEY_URL, url)
            it.putParcelable(EmailProtectionChooseEmailDialog.KEY_RESULT, result)
        }
    }

    private class FakePluginPoint : PluginPoint<DataAutofilledListener> {
        override fun getPlugins(): Collection<DataAutofilledListener> {
            return emptyList()
        }
    }
}
